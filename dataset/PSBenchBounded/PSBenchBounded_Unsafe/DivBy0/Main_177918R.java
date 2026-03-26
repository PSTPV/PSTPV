public class Main_177918R {
  public static void test(int n, int x, int t) {

    if (n <= x) {
      System.out.println(t);
      return;
    }
    long ans = 0;
    ans += (n / x) * t;
    n %= t;
    if (n != 0) {
      ans += t;
    }

    System.out.println(ans);
  }
}
