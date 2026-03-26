public class Main_119685R {
  public static void test(int b, int c) {
    int count = 0;
    for (int i = b; i <= c; i++) {
      if (c / i == 0) {
        count++;
      }
    }
    System.out.println(count);
  }
}