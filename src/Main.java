import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception{
        PowerService p = new PowerService();
        /*List<DamagedPostalCodes> l1 = p.mostDamagedPostalCodes(2);
        List<HubImpact> l2 = p.fixOrder(2);
        int pos = p.peopleOutOfService();
        List<String> s1 = p.underservedPostalByArea(1);
        List<String> s2 = p.underservedPostalByPopulation(3);
        List<Integer> rate = p.rateOfServiceRestoration((float)0.1);
        p.hubDamage("A1", 25);
        p.hubRepair("A1", "emp2", 0, false);*/
        int x = p.peopleOutOfService();
        List<Integer> l1 = p.rateOfServiceRestoration(0.05f);
        List<HubImpact> l2 = p.fixOrder(40);
        List<DamagedPostalCodes> w1 = p.mostDamagedPostalCodes(100);
        List<String> w2 = p.underservedPostalByArea(1);
        List<String> w3 = p.underservedPostalByPopulation(1000);
        List<Integer> w4 = p.rateOfServiceRestoration(0.99f);
        int a = 0;
    }
}