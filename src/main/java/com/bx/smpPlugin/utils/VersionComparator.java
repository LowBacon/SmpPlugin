// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import java.util.Locale;

public final class VersionComparator {

    private VersionComparator() {
    }

    public static int compare(String a, String b) {
        String[] left = split(a);
        String[] right = split(b);
        int core = compareDotted(left[0], right[0]);
        if (core != 0) {
            return core;
        }
        // Same release number: no pre-release tag beats any pre-release tag.
        if (left[1].isEmpty() || right[1].isEmpty()) {
            return left[1].isEmpty() == right[1].isEmpty() ? 0 : (left[1].isEmpty() ? 1 : -1);
        }
        return compareDotted(left[1], right[1]);
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static String[] split(String version) {
        String clean = version == null ? "" : version.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("v")) {
            clean = clean.substring(1);
        }
        int plus = clean.indexOf('+');
        if (plus >= 0) {
            clean = clean.substring(0, plus);
        }
        int dash = clean.indexOf('-');
        return dash < 0
                ? new String[]{clean, ""}
                : new String[]{clean.substring(0, dash), clean.substring(dash + 1)};
    }

    private static int compareDotted(String a, String b) {
        String[] left = a.isEmpty() ? new String[0] : a.split("[.]");
        String[] right = b.isEmpty() ? new String[0] : b.split("[.]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            String l = i < left.length ? left[i] : "0";
            String r = i < right.length ? right[i] : "0";
            boolean lNum = l.matches("[0-9]+");
            boolean rNum = r.matches("[0-9]+");
            int result;
            if (lNum && rNum) {
                result = Long.compare(Long.parseLong(l), Long.parseLong(r));
            } else if (lNum != rNum) {
                result = lNum ? -1 : 1; // numbers sort below words ("alpha.1" < "alpha.beta")
            } else {
                result = l.compareTo(r);
            }
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }
}
