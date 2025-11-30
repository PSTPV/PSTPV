public class Power3Bit {
    public static int power3Bit(int n) {
        int x = 3;
        int MAX_LENGTH = 10;
        int MKS = 99;
        int base = 0;
        int res = 1;
        String s = null;
        if(n < 0){
            n = -n;
        }
        if(n > 0){
            if(n < 5){
                for(int i = 0; i < n; i++){
                    res = res * x;
                    String resStr = new String(String.valueOf(res));
                    s = resStr;
                    if(i == 2 && res > MKS){
                        base = 3;
                    }
                }
            }else{
                res = MAX_LENGTH;
                String resStr = new String(String.valueOf(res));
                s = resStr;
                base = 2;
            }
        }
        res = s.length();
        return res + base;
    }
}