public class HeatPredictor_S {

    public static double predictHeat(int level, int factor) {
        int adjusted = level;
        int heat = 0;
        boolean normal = true;
        int offset = 3;

        adjusted += 6;
        adjusted *= 2;
        adjusted -= offset;
        adjusted = adjusted + 4;

        if(adjusted < 0){
            return 0.0;
        }

        if (adjusted > 30) {
            adjusted += 5;

            if (factor <= 8) {
                adjusted = adjusted + factor;

                if (level < 5) {
                    int divisor = factor - 8;
                    heat = adjusted / divisor;
                } else {
                    adjusted += 7;
                    heat = adjusted * 2;
                    normal = false;
                }

                if (heat > 100) {
                    heat = heat * 3;
                } else {
                    heat = heat + 12;
                }

            } else {
                adjusted -= 4;
                heat = adjusted * 2;
                normal = false;
            }

        } else {
            adjusted -= 6;

            if (factor < 3) {
                adjusted += 5;
                heat = adjusted * 3;
            } else {
                heat = adjusted * 2;
                normal = false;
            }

            if (heat < 25) {
                heat = heat + 5;
            } else {
                heat = heat * 3;
            }
        }

        double correction = normal ? 1.1 : 0.84;
        double finalHeat = heat * correction + adjusted * 0.05;
        System.out.println("Heat result: " + finalHeat);
        return finalHeat;
    }
}
