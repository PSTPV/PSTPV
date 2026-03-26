public class FuelEfficiencyAnalyzer_S {

    public static double analyzeEfficiency(int distance, int fuelUsed) {
        int adjustedDistance = distance;
        int threshold = 100;
        int efficiency = 0;
        boolean valid = true;

        adjustedDistance += 10;
        adjustedDistance = adjustedDistance * 2;

        if (adjustedDistance > threshold) {
            int amt = fuelUsed - 3;

            if (amt <= 0) {
                System.out.println("Warning: invalid fuel input.");
                amt = 1;
                valid = false;
            }

            if (adjustedDistance % 2 == 0) {
                efficiency = adjustedDistance / amt;
            } else {
                adjustedDistance += 5;
                efficiency =  adjustedDistance - 2 + amt;
            }

            if (efficiency > 50) {
                efficiency = efficiency * 3;
            } else {
                efficiency = efficiency + 3;
            }

        } else {
            adjustedDistance += 20;

            if (fuelUsed > 10) {
                efficiency = adjustedDistance / 2;
            } else {
                efficiency = adjustedDistance / 3;
                valid = false;
            }
        }
        double correction = valid ? 1.2 : 0.8;
        double finalValue = efficiency * correction + adjustedDistance * 0.05;

        return finalValue;
    }
}
