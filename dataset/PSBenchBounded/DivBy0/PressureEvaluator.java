public class PressureEvaluator {

    public static double computePressure(int base, int factor) {
        int adjusted = base;
        double pressure = 0.0;
        boolean stable = true;
        int modifier = 5;
        int calFactor = 2;

        adjusted += 10;
        adjusted -= factor / 2;
        adjusted *= 3;
        adjusted = adjusted - modifier;

        if (adjusted > 0 && adjusted < 10 * calFactor) {
            adjusted += 7;

            if (factor > 0) {
                int divisor = factor - 2;
                pressure = (double) adjusted / divisor;
            } else {
                adjusted = adjusted + 4;
                pressure = adjusted * 1.1;
                stable = false;
            }

            if (pressure > 30) {
                pressure = pressure * 0.9;
            } else {
                pressure = pressure + 5;
            }

        } else {
            adjusted -= 3;

            if (factor < 10) {
                adjusted += 6;
                pressure = adjusted * 0.7;
            } else {
                pressure = adjusted * 0.4;
                stable = false;
            }

            if (pressure < 20) {
                pressure = pressure + 3;
            } else {
                pressure = pressure * 0.95;
            }
        }

        double correction = stable ? 1.15 : 0.85;
        double finalValue = pressure * correction + adjusted * 0.05;
        System.out.println("Pressure result: " + finalValue);
        return finalValue;
    }
}
