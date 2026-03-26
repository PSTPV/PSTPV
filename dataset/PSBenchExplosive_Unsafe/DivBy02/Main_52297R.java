public class Main_52297R {
  public static void test(int n, int m, int p,int x) {
      int xm = 0;
      int sum = 0;
      for (int i = 0; i < n; i++) {
        sum += x;
        if (i + 1 == m)
          xm = x;
      }
      int res = 0;
      res = m == 0 ? 0 : (sum * (100 - p)) / xm;
      System.out.println((int)res);
  }
}