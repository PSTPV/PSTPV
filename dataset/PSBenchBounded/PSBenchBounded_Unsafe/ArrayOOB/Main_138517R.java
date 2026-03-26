import java.util.*;
public class Main_138517R {
  public static void test(int ch,int d) {
    int[] s = new int[52];
    for (int i = 0; i < 52; i++) {
      if (ch == 30)
        s[d] = 1;
    }
    for (int i = 1; i < 52; i++) {
      if (s[i] == 0) {
        if (i < 14)
          System.out.println("S " + i);
        else if (i < 27)
          System.out.println("H " + (i - 13));
        else if (i < 40)
          System.out.println("C " + (i - 26));
        else
          System.out.println("D " + (i - 39));
      }
    }
  }
}