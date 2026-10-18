package net.bakaar.authz.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Checks the application colors of {@code admin.css} in both themes: chip text passes WCAG AA on its background,
 * and app-5 is clearly apart from the other colors (it used to be a lilac next to app-2's sky).
 */
class AdminPaletteTest {

    private static final Pattern VARIABLE = Pattern.compile("--([a-z-]+):\\s*(#[0-9a-fA-F]{6})");
    private static final Pattern APP_CLASS = Pattern.compile(
            "\\.app-(\\d) \\{ --app-bg: var\\(--([a-z-]+)\\);\\s*--app-text: var\\(--([a-z-]+)\\); }");

    private final String css = css();

    @Test
    void chipTextPassesAaInBothThemes() {
        for (Map<String, String> theme : List.of(light(), darkByChoice(), darkBySystem())) {
            apps().forEach((app, vars) -> assertThat(contrast(theme.get(vars[0]), theme.get(vars[1])))
                    .as("contrast of app-%s", app).isGreaterThanOrEqualTo(4.5));
        }
    }

    @Test
    void app5IsClearlyApartFromEveryOtherApplicationColor() {
        for (Map<String, String> theme : List.of(light(), darkByChoice())) {
            Map<Integer, String[]> apps = apps();
            String app5 = theme.get(apps.get(5)[0]);
            assertThat(deltaE(app5, theme.get(apps.get(2)[0]))).as("app-5 vs app-2 background").isGreaterThan(20);
            apps.forEach((app, vars) -> {
                if (app != 5) {
                    assertThat(deltaE(app5, theme.get(vars[0]))).as("app-5 vs app-%s background", app)
                            .isGreaterThan(10);
                }
            });
        }
    }

    @Test
    void bothDarkBlocksDefineTheSameApplicationColors() {
        Map<String, String> byChoice = darkByChoice();
        Map<String, String> bySystem = darkBySystem();
        apps().values().forEach(vars -> {
            assertThat(bySystem.get(vars[0])).isEqualTo(byChoice.get(vars[0]));
            assertThat(bySystem.get(vars[1])).isEqualTo(byChoice.get(vars[1]));
        });
    }

    /** app number to {background variable, text variable}. */
    private Map<Integer, String[]> apps() {
        Map<Integer, String[]> apps = new TreeMap<>();
        Matcher matcher = APP_CLASS.matcher(css);
        while (matcher.find()) {
            apps.put(Integer.parseInt(matcher.group(1)), new String[] {matcher.group(2), matcher.group(3)});
        }
        assertThat(apps).hasSize(8);
        return apps;
    }

    private Map<String, String> light() {
        return variables(block(":root {"));
    }

    private Map<String, String> darkByChoice() {
        return variables(block(":root[data-theme=dark] {"));
    }

    private Map<String, String> darkBySystem() {
        return variables(block(":root:not([data-theme=light]) {"));
    }

    private String block(String start) {
        int from = css.indexOf(start);
        assertThat(from).as(start).isNotNegative();
        return css.substring(from, css.indexOf('}', from));
    }

    private static Map<String, String> variables(String block) {
        Map<String, String> variables = new HashMap<>();
        Matcher matcher = VARIABLE.matcher(block);
        while (matcher.find()) {
            variables.put(matcher.group(1), matcher.group(2));
        }
        return variables;
    }

    private static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        double[] rgb = linear(hex);
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    /** CIE76 distance in CIELAB (D65): about 2.3 is just noticeable, above 10 clearly different. */
    private static double deltaE(String a, String b) {
        double[] x = lab(a);
        double[] y = lab(b);
        return Math.sqrt(Math.pow(x[0] - y[0], 2) + Math.pow(x[1] - y[1], 2) + Math.pow(x[2] - y[2], 2));
    }

    private static double[] lab(String hex) {
        double[] rgb = linear(hex);
        double x = (0.4124 * rgb[0] + 0.3576 * rgb[1] + 0.1805 * rgb[2]) / 0.95047;
        double y = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
        double z = (0.0193 * rgb[0] + 0.1192 * rgb[1] + 0.9505 * rgb[2]) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }

    private static double[] linear(String hex) {
        double[] rgb = new double[3];
        for (int i = 0; i < 3; i++) {
            double c = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            rgb[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        return rgb;
    }

    private static String css() {
        try (InputStream in = AdminPaletteTest.class.getResourceAsStream("/admin-static/admin.css")) {
            assertThat(in).as("admin.css on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
