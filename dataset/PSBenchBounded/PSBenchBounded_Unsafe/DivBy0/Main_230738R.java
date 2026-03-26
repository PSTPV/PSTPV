public class Main_230738R {
  public static void test(int A, int V, int B, int W, int T) {

    boolean catched = false;

    int dist = Math.abs(A - B);
    int dv = V - W;

    if (dv <= 0) {
      catched = false;
    } else if (dv >= dist / T) {
      catched = true;
    }

    if (catched) {
      System.out.println("YES");
    } else {
      System.out.println("NO");
    }

  }
}
