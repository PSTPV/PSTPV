public class RiskScoreCalculator_S {

    public static double calculateRisk(int age, int incidents) {
        if(age < 16 || age > 60){
            return -1;
        }

        int score = age / 2;
        int avg = 41;
        score = score + incidents * 7;
        score = score - 5;
        int factor = 5 * incidents + 1;

        double penalty = 1.5;
        double adjustment = 0.0;
        boolean risky = false;

        if (score > 80) {
            factor = factor - avg;
            score = score - 5;
            if(score < 150){
                score = (int) (score - avg * penalty);
            }else{
                score = 150;
            }
            adjustment = (double) score / factor;
            risky = adjustment > 10;
        } else {
            score = score + 7;
            factor = factor - 1;
            adjustment = score * 0.5;
            risky = adjustment < 5;
        }

        double finalScore = adjustment + score * 0.3 + factor;
        if (risky) {
            finalScore += 2.5;
        } else {
            finalScore -= 1.2 * penalty;
        }
        return finalScore;
    }
}
