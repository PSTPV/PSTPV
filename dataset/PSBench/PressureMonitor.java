public class PressureMonitor {
    public static void evaluatePressure(int pressure, int limit) {
        int ratio = 0;
        int warningLevel = 5;
        double stability = 1.2;
        boolean status = true;

        if (limit == 0 || limit == 2) {
            limit += 3;
        }

        while (limit > 0 && limit < 4) {
            limit = limit - 2;
            warningLevel += 3;
            stability = stability * 1.1;

            if (pressure > 5 && pressure < 8) {
                limit = limit - 1;
            }
        }

        ratio = pressure / limit;

        System.out.println("warningLevel is" + warningLevel);
        if (status && ratio < 20) {
            System.out.println("Pressure level acceptable, ratio: " + ratio);
        } else {
            System.out.println("Warning: abnormal ratio detected!");
        }
    }
}
