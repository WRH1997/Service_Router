import java.util.*;
import java.sql.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception{
        PowerService p = new PowerService();
        System.out.println(p.peopleOutOfService());
        List<DamagedPostalCodes> l1 = p.mostDamagedPostalCodes(1);
        for(DamagedPostalCodes dpc: l1){
            System.out.println(dpc.getPostalCodeId());
        }
        List<String> l2 = p.underservedPostalByPopulation(1);
        List<String> l3 = p.underservedPostalByArea(1);
        System.out.println(l2+"\n"+l3);
        System.out.println(p.rateOfServiceRestoration(0.1f));
        List<HubImpact> l4 = p.fixOrder(100);
        for(HubImpact hub: l4){
            System.out.println(hub.getHubId());
        }
        System.out.println();
        List<HubImpact> l5 = p.repairPlan("a  1", 10, 20);
        for(HubImpact hub: l5){
            System.out.println(hub.getHubId());
        }
        //p.hubDamage("b2", 2.5f);
        /*System.out.println(p.peopleOutOfService());
        List<DamagedPostalCodes> l1 = p.mostDamagedPostalCodes(100);
        for(DamagedPostalCodes dpc: l1){
            System.out.println(dpc.getPostalCodeId());
        }
        System.out.println();
        List<String> l2 = p.underservedPostalByPopulation(1);
        List<String> l3 = p.underservedPostalByArea(1);
        System.out.println(l2+"\n"+l3);
        System.out.println(p.rateOfServiceRestoration(0.05f));
        System.out.println(p.mostDamagedPostalCodes(100));
        System.out.println(p.underservedPostalByArea(100));
        System.out.println(p.underservedPostalByPopulation(100));
        System.out.println(p.fixOrder(100));
        System.out.println(p.repairPlan("A1", 100, 100));*/
    }
}