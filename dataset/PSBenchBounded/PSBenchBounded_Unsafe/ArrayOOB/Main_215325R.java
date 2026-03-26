import java.util.*;
// fail on overtime
public class Main_215325R {
  public static void test(int n, int k, int n1) {
    int[] arr = new int[5];
    for (int i = 0; i < n && n < 200; i++) {
      arr[i] = n1;
    }
    Arrays.sort(arr);
    int sum = 0;
    for (int i = 0; i < k; i++) {
      sum += arr[i];
    }
    System.out.println(sum);
  }
}