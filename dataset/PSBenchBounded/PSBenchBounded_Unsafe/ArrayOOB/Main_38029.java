import java.util.*;
public class Main_38029 {
  public static void test(int n1,int n2) {
    int wt = 0, kt = 0;
    int[] w = new int[9];
    int[] k = new int[9];
    for (int i = 0; i < 10; i++) {
      w[i] = n1;
    }
    for (int i = 0; i < 10; i++) {
      k[i] = n2;
    }
    Arrays.sort(w);
    wt = w[9] + w[8] + w[7];
    Arrays.sort(k);
    kt = k[9] + k[8] + k[7];
    System.out.println(wt + " " + kt);
  }
}