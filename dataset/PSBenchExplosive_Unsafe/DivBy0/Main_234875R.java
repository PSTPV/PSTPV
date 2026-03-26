public class Main_234875R {
  public static void test(int N, int A) {

    int max = 100;
    int ans = 1;
    int Amax = 0;
    boolean of = false;
    for (int i = 0; i < N && N < max; i++) {
      if (A == 0) {
        ans = 0;
        of = false;
      }
      if (A > Amax) {
        of = true;
      }
      if(A <= Amax){
        ans *= A;
      }
    }
    Amax = max / ans;

    System.out.println(of ? -1 : ans);

  }
}
