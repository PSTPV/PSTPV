public class FlowCalculator_S {

    public static double computeFlow(int pressure, int rate) {
        int adjusted = pressure;
        int flow = 0;
        boolean stable = true;
        int modifier = 3;

        adjusted += 7;
        adjusted *= 2;
        adjusted -= modifier;
        adjusted = adjusted + 4;

        if (adjusted > 40) {
            adjusted += 6;

            if (rate >= 5) {
                adjusted = adjusted + rate;

                if (pressure < 10) {
                    int divisor = rate - 5;
                    flow =  adjusted / divisor;
                } else {
                    adjusted += 8;
                    flow = adjusted * 3;
                    stable = false;
                }

                if (flow > 80) {
                    flow = flow / 2;
                } else {
                    flow = flow + 10;
                }

            } else {
                adjusted -= 4;
                flow = adjusted * 3;
                stable = false;
            }

        } else {
            adjusted -= 6;

            if (rate < 3) {
                adjusted += 5;
                flow = adjusted * 6;
            } else {
                flow = adjusted * 5;
                stable = false;
            }

            if (flow < 20) {
                flow = flow + 5;
            } else {
                flow = flow * 3;
            }
        }

        double correction = stable ? 1.1 : 0.85;
        double finalFlow = flow * correction + adjusted * 0.05;
        System.out.println("Flow result: " + finalFlow);
        return finalFlow;
    }
}
