public class PressureEvaluator {

    public static double computePressure(int base, int factor) {
        int adjusted = base;
        int pressure = 0;
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
                pressure = adjusted / divisor;
            } else {
                adjusted = adjusted + 4;
                pressure = adjusted * 2;
                stable = false;
            }

            if (pressure > 30) {
                pressure = pressure * 3;
            } else {
                pressure = pressure + 5;
            }

        } else {
            adjusted -= 3;

            if (factor < 10) {
                adjusted += 6;
                pressure = adjusted * 2;
            } else {
                pressure = adjusted * 3;
                stable = false;
            }

            if (pressure < 20) {
                pressure = pressure + 3;
            } else {
                pressure = pressure * 2;
            }
        }

        double correction = stable ? 1.15 : 0.85;
        double finalValue = pressure * correction + adjusted * 0.05;
        System.out.println("Pressure result: " + finalValue);
        return finalValue;
    }
}
