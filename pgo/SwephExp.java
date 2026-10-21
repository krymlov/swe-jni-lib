/* PGO training run and result check for the JNI library (build_dll.bat).
 *
 *   java pgo\SwephExp.java <path to swe-<version>.dll> <ephe dir> [repeats]
 *   java pgo\SwephExp.java <path to swe-<version>.dll> <ephe dir> 1 verify
 *
 * A Java port of swe_bench.c from swisseph-2026: the same workload (sidereal
 * True Citra with SEFLG_TRUEPOS and speeds, tropical, houses, sunrise / sunset;
 * phases modern / random / bounds), but called through the JNI entry points,
 * so the training profile covers swejni.c as well. Prints the best time of the
 * repeats and an FNV-1a 64-bit checksum over the bytes of every result
 * (doubles, return codes, error strings, little endian as in C). Two DLLs with
 * the same checksum give the same results, bit for bit, on this workload.
 *
 * The second form runs a wider workload (other flags, house systems,
 * ayanamsas, fixed stars, eclipses), not timed and not used for training,
 * and prints only its checksum.
 *
 * The class is swisseph.SwephExp, so that the JNI names of the library
 * (Java_swisseph_SwephExp_*) resolve; it declares only the natives it calls.
 * Run in source-file mode (JDK 11+), nothing to compile. ASCII only.
 */
package swisseph;

import java.nio.charset.StandardCharsets;

public class SwephExp {
    static native int swe_calc(double tjd, int ipl, int iflag, double[] xx, StringBuilder serr);
    static native int swe_calc_ut(double tjd_ut, int ipl, int iflag, double[] xx, StringBuilder serr);
    static native int swe_fixstar_ut(StringBuilder star, double tjd_ut, int iflag, double[] xx, StringBuilder serr);
    static native void swe_close();
    static native void swe_set_ephe_path(String path);
    static native void swe_set_sid_mode(int sid_mode, double t0, double ayan_t0);
    static native int swe_get_ayanamsa_ex_ut(double tjd_ut, int iflag, double[] daya, StringBuilder serr);
    static native double swe_julday(int year, int month, int day, double hour, int gregflag);
    static native int swe_houses_ex(double tjd_ut, int iflag, double geolat, double geolon, int hsys,
                                    double[] cusps, double[] ascmc);
    static native int swe_sol_eclipse_where(double tjd, int ifl, double[] geopos, double[] attr, StringBuilder serr);
    static native int swe_sol_eclipse_when_glob(double tjd_start, int ifl, int ifltype, double[] tret,
                                                int backward, StringBuilder serr);
    static native int swe_lun_eclipse_when(double tjd_start, int ifl, int ifltype, double[] tret,
                                           int backward, StringBuilder serr);
    static native int swe_rise_trans(double tjd_ut, int ipl, StringBuilder starname, int epheflag, int rsmi,
                                     double[] geopos, double atpress, double attemp, double[] tret,
                                     StringBuilder serr);
    static native double swe_deltat_ex(double tjd, int iflag, StringBuilder serr);

    static final int SEFLG_SWIEPH = 2, SEFLG_SPEED = 256, SEFLG_TRUEPOS = 16, SEFLG_SIDEREAL = 64 * 1024;
    static final int SE_CALC_RISE = 1, SE_CALC_SET = 2, SE_BIT_DISC_CENTER = 256, SE_BIT_NO_REFRACTION = 512;
    static final int[] BODIES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10};

    static long h;

    static void mixByte(int b) {
        h ^= b & 0xff;
        h *= 1099511628211L;
    }

    static void mix(int v) {
        for (int i = 0; i < 4; i++) mixByte(v >>> (8 * i));
    }

    static void mix(double d) {
        long v = Double.doubleToRawLongBits(d);
        for (int i = 0; i < 8; i++) mixByte((int) (v >>> (8 * i)));
    }

    static void mix(double[] a, int n) {
        for (int i = 0; i < n; i++) mix(a[i]);
    }

    static void mix(CharSequence s) {
        for (byte b : s.toString().getBytes(StandardCharsets.ISO_8859_1)) mixByte(b);
    }

    static void one(double jd, boolean withRise) {
        double[] x = new double[6], cusp = new double[13], ascmc = new double[10], t = new double[10];
        StringBuilder serr = new StringBuilder();
        int fSid = SEFLG_SWIEPH | SEFLG_SPEED | SEFLG_SIDEREAL | SEFLG_TRUEPOS;
        int fTrop = SEFLG_SWIEPH | SEFLG_SPEED;
        int r;
        for (int body : BODIES) {
            serr.setLength(0);
            r = swe_calc_ut(jd, body, fSid, x, serr);
            mix(r); mix(x, 6); mix(serr);
            serr.setLength(0);
            r = swe_calc_ut(jd, body, fTrop, x, serr);
            mix(r); mix(x, 6); mix(serr);
        }
        r = swe_houses_ex(jd, SEFLG_SIDEREAL, 16.166667, 81.133333, 'P', cusp, ascmc);
        mix(r); mix(cusp, 13); mix(ascmc, 10);
        if (withRise) {
            double[] geo = {81.133333, 16.166667, 0};
            serr.setLength(0);
            r = swe_rise_trans(jd - 1, 0, null, SEFLG_SWIEPH,
                    SE_CALC_RISE | SE_BIT_DISC_CENTER | SE_BIT_NO_REFRACTION, geo, 0, 0, t, serr);
            mix(r); mix(t[0]); mix(serr);
            serr.setLength(0);
            r = swe_rise_trans(jd - 1, 0, null, SEFLG_SWIEPH,
                    SE_CALC_SET | SE_BIT_DISC_CENTER | SE_BIT_NO_REFRACTION, geo, 0, 0, t, serr);
            mix(r); mix(t[0]); mix(serr);
        }
    }

    static long run(double[] ms, String ephe) {
        double jd, j0, j1;
        int seed = 12345;
        long a;
        h = 0xcbf29ce484222325L;
        swe_set_ephe_path(ephe);
        swe_set_sid_mode(27, 0, 0);

        a = System.nanoTime();
        j0 = swe_julday(1900, 1, 1, 0, 1);
        j1 = swe_julday(2100, 1, 1, 0, 1);
        int i = 0;
        for (jd = j0; jd < j1; jd += 3.7, i++)
            one(jd, i % 4 == 0);
        ms[0] = (System.nanoTime() - a) / 1e6;

        a = System.nanoTime();
        j0 = swe_julday(-5000, 1, 1, 0, 0);
        j1 = swe_julday(5000, 1, 1, 0, 1);
        for (i = 0; i < 6000; i++) {
            seed = seed * 1103515245 + 12345;
            jd = j0 + (j1 - j0) * ((seed >>> 8) / 16777216.0);
            one(jd, i % 10 == 0);
        }
        ms[1] = (System.nanoTime() - a) / 1e6;

        a = System.nanoTime();
        for (int y = -5400; y <= 5400; y += 600) {
            double jb = swe_julday(y, 1, 1, 0, y >= 1600 ? 1 : 0);
            for (i = -20; i <= 20; i++) {
                one(jb + i * 13.7, false);
                one(jb - i * 13.7 + 0.5, false);
            }
        }
        ms[2] = (System.nanoTime() - a) / 1e6;
        swe_close();
        return h;
    }

    static long verify(String ephe) {
        final int[] flags = {
            2, 2 | 256, 2 | 256 | 2048, 2 | 256 | 8, 2 | 256 | 32 * 1024, 2 | 256 | 16 | 1024,
            2 | 256 | 64 * 1024, 2 | 256 | 64 * 1024 | 4096, 2 | 256 | 512, 2 | 256 | 4 * 1024 * 1024 / 8
        };
        final String hsys = "PKORCAEWBMUTXHGV";
        final int[] sids = {0, 1, 3, 5, 7, 21, 27, 29, 30, 39, 40, 41, 42};
        final String[] stars = {",alVir", "Aldebaran", "Regulus", "Antares", ",bePer", "Sirius", "Pollux"};
        double[] x = new double[20], cusp = new double[37], ascmc = new double[10], tret = new double[10];
        double[] geo = new double[10], d = new double[1];
        StringBuilder serr = new StringBuilder(), star = new StringBuilder();
        int seed = 777, r;
        h = 0xcbf29ce484222325L;
        swe_set_ephe_path(ephe);
        for (int i = 0; i < 3000; i++) {
            seed = seed * 1103515245 + 12345;
            double jd = swe_julday(-3000, 1, 1, 0, 0) + 2191500.0 * ((seed >>> 8) / 16777216.0);
            swe_set_sid_mode(sids[i % sids.length], 0, 0);
            for (int k = 0; k <= 22; k++) {
                int fl = flags[(i + k) % flags.length];
                serr.setLength(0);
                r = (k & 1) != 0 ? swe_calc(jd, k == 22 ? 15 : k, fl, x, serr)
                                 : swe_calc_ut(jd, k == 22 ? 15 : k, fl, x, serr);
                mix(r); mix(x, 6); mix(serr);
            }
            serr.setLength(0);
            r = swe_get_ayanamsa_ex_ut(jd, 2 | 256, d, serr);
            mix(r); mix(d[0]);
            mix(swe_deltat_ex(jd, 2, serr));
            r = swe_houses_ex(jd, (i & 1) != 0 ? 64 * 1024 : 0, -60 + (i % 121), -180 + (i * 7 % 360),
                    hsys.charAt(i % 16), cusp, ascmc);
            mix(r); mix(cusp, 13); mix(ascmc, 10);
            if (i % 5 == 0) {
                star.setLength(0);
                star.append(stars[i % 7]);
                serr.setLength(0);
                r = swe_fixstar_ut(star, jd, 2 | 256 | ((i & 2) != 0 ? 64 * 1024 : 0), x, serr);
                mix(r); mix(x, 6); mix(star); mix(serr);
            }
            if (i % 20 == 0) {
                geo[0] = -120 + i % 240; geo[1] = -50 + i % 100; geo[2] = i % 500;
                serr.setLength(0);
                r = swe_rise_trans(jd, i % 10, null, 2, 1 + (i / 20) % 4, geo, 1013.25, 15, tret, serr);
                mix(r); mix(tret[0]); mix(serr);
            }
            if (i % 60 == 0 && jd > 1000000) {
                serr.setLength(0);
                r = swe_sol_eclipse_when_glob(jd, 2, 0, tret, 0, serr); mix(r); mix(tret, 10);
                r = swe_sol_eclipse_where(tret[0], 2, geo, x, serr); mix(r); mix(geo, 2);
                r = swe_lun_eclipse_when(jd, 2, 0, tret, 0, serr); mix(r); mix(tret, 10);
            }
        }
        swe_close();
        return h;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: java SwephExp.java <swe-<version>.dll> <ephe dir> [repeats] [verify]");
            System.exit(2);
        }
        System.load(new java.io.File(args[0]).getAbsolutePath());
        String ephe = args[1];
        int reps = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        if (args.length > 3 && args[3].equals("verify")) {
            System.out.printf("verify %016x%n", verify(ephe));
            return;
        }
        double[] best = {1e30, 1e30, 1e30}, t = new double[3];
        long sum = 0;
        for (int k = 0; k < reps; k++) {
            long s = run(t, ephe);
            if (k == 0) sum = s;
            else if (s != sum) {
                System.err.println("checksum differs between repeats");
                System.exit(1);
            }
            for (int j = 0; j < 3; j++) best[j] = Math.min(best[j], t[j]);
        }
        System.out.printf(java.util.Locale.ROOT,
                "checksum %016x  modern %.1f ms  random %.1f ms  bounds %.1f ms  total %.1f ms%n",
                sum, best[0], best[1], best[2], best[0] + best[1] + best[2]);
    }
}
