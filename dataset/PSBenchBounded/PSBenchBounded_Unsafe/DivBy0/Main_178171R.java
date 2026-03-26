public class Main_178171R {
  public static void test(int N, int X, int T) {

    int ans = 0;
    ans = N / X;
    if (N % T != 0) {
      ans++;
    }
    System.out.println(ans * T);
  }
}