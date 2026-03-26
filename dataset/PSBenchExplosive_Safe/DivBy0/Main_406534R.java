public class Main_406534R {

  public static void test(int n) {

    int i;
    int ans = 0;
    for (i = 1; i * i < n; i++) {
      if (n % i == 0)
        ans = i;
    }
    System.out.println(ans + n / ans - 2);
  }
}
