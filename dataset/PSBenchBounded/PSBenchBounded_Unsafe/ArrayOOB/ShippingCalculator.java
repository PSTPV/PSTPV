public class ShippingCalculator {
    public static int calculateCost(int weight, int distance) {
        int rateTiers[] = new int[3];
        rateTiers[0] = 5;
        rateTiers[1] = 8;
        rateTiers[2] = 12;
        
        int baseFee = 10;
        int surcharge = 0;
        int total = 0;
        
        if (weight <= 0 || distance <= 0) {
            return -1;
        }
        
        if (weight > 50) {
            return weight * rateTiers[2] + distance / 10;
        }
        
        int weightTier = weight / 10;
        if (weightTier < 0) weightTier = 0;
        
        if (distance > 100) {
            surcharge = distance / 50;
        }
        
        if (weightTier == 0) {
            total = baseFee + distance * rateTiers[0] / 10;
            int t = 0;
            t = (total/10) < 5 ? (total/10) : 5;
            total = total - t;
        } else if (weightTier == 1) {
            total = baseFee + (weight - 10) * rateTiers[1] + 10 * rateTiers[0];
            total = total + surcharge * 2;
        } else {
            total = weight * rateTiers[weightTier] + distance / 20;
            if (total > 100) {
                total = total + rateTiers[weightTier] * 5;
            }
        }
        return total;
    }
}