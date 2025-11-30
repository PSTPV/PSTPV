import java.util.ArrayList;

public class SensorCalibrator {
    public static int calibrateReading(int rawValue) {
        ArrayList<Integer> calibrationCurve = new ArrayList<Integer>(16);
        for (int i = 0; i < 16; i++) {
            calibrationCurve.add(0);
        }
        int MAX_RAW = 1023;
        int MIN_RAW = 0;
        int offset = 0;

        int curveIndex = 0;

        if (rawValue < MIN_RAW) {
            rawValue = MIN_RAW;
            offset = offset - 8;
        } else {
            offset = offset + 4;
        }

        if (rawValue > MAX_RAW) {
            rawValue = MAX_RAW;
            offset = offset + 12;
            System.out.println("The offset is " + offset);
        }

        int calibrated = rawValue / 2 + 25;

        if (calibrated >= 50 && calibrated < 100) {
            curveIndex = calibrated / 6;
        } else {
            curveIndex = calibrated % 10;
        }

        calibrationCurve.get(curveIndex);
        return calibrated;
    }
}