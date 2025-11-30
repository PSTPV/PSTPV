import java.util.ArrayList;

public class RenderEngine {
    public static int calculateFrameRate(int resolution) {
        ArrayList<Integer> fpsTiers = new ArrayList<Integer>(9);
        for (int i = 0; i < 9; i++) {
            fpsTiers.add(0);
        }
        int MAX_RES = 4096;
        int MIN_RES = 480;
        int optimization = 0;

        int tierIndex = 0;

        if(0 < resolution &&resolution < MIN_RES - 200){
            return MIN_RES - resolution;
        }

        if (resolution < MIN_RES) {
            resolution = MIN_RES;
            optimization = optimization - 5;
        } else {
            optimization = optimization + 2;
        }

        if (resolution > MAX_RES) {
            resolution = MAX_RES;
            optimization = optimization + 8;
        }

        int frameRate = resolution / 80 + 30;

        if (frameRate >= 40 && frameRate <= 80) {
            tierIndex = (frameRate - 30) / 6;
        } else {
            tierIndex = frameRate % 9;
        }

        fpsTiers.get(tierIndex);

        if(optimization > 15){
            frameRate = frameRate + optimization;
        } else {
            frameRate = frameRate - optimization;
        }
        return frameRate;
    }
}