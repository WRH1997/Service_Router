import java.io.IOException;
import java.sql.*;
import java.util.*;

public class PowerService{

    private Map<String, DamagedPostalCodes> postalCodes;
    private Map<String, HubImpact> distributionHubs;
    private Database db;

    PowerService() throws Exception{
        try {
            db = new Database();
        }
        catch(IOException e){
            throw new IOException("Error reading credentials file (prop file)!\nSource: PowerService constructor\nDetails: " + e.getMessage());
        }
        catch(SQLException e){
            throw new SQLException("Database connection failed!\nSource: PowerService constructor\nDetails: " + e.getMessage());
        }
        postalCodes = new HashMap<>();
        distributionHubs = new HashMap<>();
        List<DamagedPostalCodes> existingPostalCodes = new ArrayList<>();
        try{
            existingPostalCodes = db.getPostalCodesFromDB();
        }
        catch(SQLException e){
            throw new SQLException("SQL query failed (selecting from PostalCodes table)!\nSource: PowerService constructor\nDetails: " + e.getMessage());
        }
        if(existingPostalCodes!=null && !existingPostalCodes.isEmpty()){
            for(int i=0; i<existingPostalCodes.size(); i++){
                postalCodes.put(existingPostalCodes.get(i).getPostalCodeId(), existingPostalCodes.get(i));
            }
        }
        List<HubImpact> existingDistributionHubs = new ArrayList<>();
        try{
            existingDistributionHubs = db.getDistributionHubsFromDB();
        }
        catch(SQLException e){
            throw new SQLException("SQL query failed (selecting from DistributionHubs table)!\nSource: PowerService constructor\nDetails: " + e.getMessage());
        }
        if(existingDistributionHubs!=null && !existingDistributionHubs.isEmpty()){
            for(int i=0; i<existingDistributionHubs.size(); i++){
                distributionHubs.put(existingDistributionHubs.get(i).getHubId(), existingDistributionHubs.get(i));
            }
        }
    }


    boolean addPostalCode(String postalCode, int population, int area) /*throws SQLException*/{
        if(postalCode==null){
            return false;
        }
        //https://stackoverflow.com/questions/5455794/removing-whitespace-from-strings-in-java
        postalCode = postalCode.replaceAll("\\s+","");
        if(postalCode.equals("")){
            return false;
        }
        postalCode = postalCode.toUpperCase();
        if(!postalCodeIsValidPattern(postalCode)){
            return false;
        }
        if(postalCodes.containsKey(postalCode)){
            return false;
        }
        if(population<0){
            return false;
        }
        if(area<1){
            return false;
        }
        DamagedPostalCodes newPostalCode = new DamagedPostalCodes(postalCode, population, area, 0);
        try{
            db.addPostalCodeToDB(newPostalCode);
        }
        catch(SQLException e){
            //which of these should be used? (consistent exceptions or boolean since method returns boolean)
            //throw new SQLException("SQL insert into PostalCodes table failed!\nSource: addPostalCode\nDetails: " + e.getMessage());
            return false;
        }
        try{
            updatePostalHubRelation(postalCode);
            newPostalCode.setRepairEstimate(db.calculatePostalRepairTime(postalCode));
        }
        catch(SQLException e){
            //which of these should be used? (consistent exceptions or boolean since method returns boolean)
            //throw new SQLException("SQL insert into PostalHubRelation table failed!\nSource: addPostalCode\nDetails: " + e.getMessage());
            return false;
        }
        postalCodes.put(newPostalCode.getPostalCodeId(), newPostalCode);
        return true;
    }


    private boolean postalCodeIsValidPattern(String postalCode){
        int counter = 0;
        for(int i=0; i<postalCode.length(); i++){
            if(counter%2==0){
                if(!Character.isLetter(postalCode.charAt(i))){
                    return false;
                }
            }
            else{
                if(!Character.isDigit(postalCode.charAt(i))){
                    return false;
                }
            }
            counter++;
        }
        return true;
    }


    private void updatePostalHubRelation(String postalCode) throws SQLException{
        for(Map.Entry<String, HubImpact> entry: distributionHubs.entrySet()){
            if(entry.getValue().getServicedAreas().contains(postalCode)){
                try{
                    db.updatePostalHubRelation(postalCode, entry.getKey());
                }
                catch(SQLException e){
                    throw e;
                }
            }
        }
    }


    boolean addDistributionHub(String hubIdentifier, Point location, Set<String> servicedAreas) /*throws SQLException*/{
        if(hubIdentifier==null){
            return false;
        }
        hubIdentifier = hubIdentifier.replaceAll("\\s+","");
        if(hubIdentifier.equals("")){
            return false;
        }
        hubIdentifier = hubIdentifier.toUpperCase();
        if(distributionHubs.containsKey(hubIdentifier)){
            return false;
        }
        if(location==null){
            return false;
        }
        if(hubExistsInLocation(location)){
            return false;
        }
        if(servicedAreas==null){
            return false;
        }
        Set<String> servicedPostalCodes = new HashSet<>();
        Iterator itr = servicedAreas.iterator();
        while(itr.hasNext()){
            String area = (String) itr.next();
            if(area==null){
                return false;
            }
            area = area.replaceAll("\\s+","");
            if(area.equals("")){
                return false;
            }
            area = area.toUpperCase();
            if(!postalCodeIsValidPattern(area)){
                return false;
            }
            servicedPostalCodes.add(area);
        }
        HubImpact newHub = new HubImpact(hubIdentifier, location, servicedPostalCodes, 0, true, 0, 0);
        try{
            db.addDistributionHubToDB(newHub);
        }
        catch(SQLException e){
            //which of these should be used? (consistent exceptions or boolean since method returns boolean)
            //throw new SQLException("SQL insert into DistributionHubs table failed!\nSource: addDistributionHub\nDetails: " + e.getMessage());
            return false;
        }
        try{
            Iterator itr2 = servicedPostalCodes.iterator();
            while(itr2.hasNext()){
                String postalCode = (String) itr2.next();
                db.updatePostalHubRelation(postalCode, hubIdentifier);
            }
        }
        catch(SQLException e){
            //which of these should be used? (consistent exceptions or boolean since method returns boolean)
            //throw new SQLException("SQL inset into PostalHubRelation table failed!\nSource: addDistributionHub\nDetails: " + e.getMessage());
            return false;
        }
        distributionHubs.put(hubIdentifier, newHub);
        return true;
    }


    boolean hubExistsInLocation(Point location){
        for(Map.Entry<String, HubImpact> entry: distributionHubs.entrySet()){
            if(entry.getValue().getLocation().compare(location)){
                return true;
            }
        }
        return false;
    }


    void hubDamage(String hubIdentifier, float repairEstimate) throws Exception{
        if(hubIdentifier==null){
            throw new IllegalArgumentException("HubIdentifier is null! \nSource: hubDamage");
        }
        hubIdentifier = hubIdentifier.replaceAll("\\s+","");
        if(hubIdentifier.equals("")){
            throw new IllegalArgumentException("HubIdentifier is empty! \nSource: hubDamage");
        }
        hubIdentifier = hubIdentifier.toUpperCase();
        if(!distributionHubs.containsKey(hubIdentifier)){
            throw new IllegalArgumentException("HubIdentifier does not exist (has not been added)! \nSource: hubDamage");
        }
        if(repairEstimate<=0){
            throw new IllegalArgumentException("RepairEstimate is zero or negative (invalid)! \nSource: hubDamage");
        }
        HubImpact hub = distributionHubs.get(hubIdentifier);
        hub.setRepairTime(hub.getRepairTime() + repairEstimate);
        hub.setInService(false);
        try{
            db.updateHubDamage(hubIdentifier, repairEstimate);
        }
        catch(SQLException e){
            throw new SQLException("SQL update to DistributionHubs table failed!\nSource: hubDamage\nDetails: " + e.getMessage());
        }
        /*try{
            Set<String> servicedAreas = distributionHubs.get(hubIdentifier).getServicedAreas();
            distributionHubs.get(hubIdentifier).setPopulationEffected(db.calculatePopulationEffected(hubIdentifier, servicedAreas));
            Iterator itr = servicedAreas.iterator();
            while(itr.hasNext()){
                String postalCode = (String) itr.next();
                postalCodes.get(postalCode).setRepairEstimate(db.calculatePostalRepairTime(postalCode));
            }
        }
        catch(SQLException e){
            throw new SQLException("SQL select from DistributionHubs, PostalCodes, PostalHubRelation tables failed!\nSource: hubDamage\nDetails: " + e.getMessage());
        }*/
        try{
            hub.setPopulationEffected(db.calculatePopulationEffected(hub.getHubId(), hub.getServicedAreas()));
            hub.setImpact(((float) hub.getPopulationEffected()) / hub.getRepairTime());
            Iterator itr = hub.getServicedAreas().iterator();
            while(itr.hasNext()){
                String postalCode = (String) itr.next();
                postalCodes.get(postalCode).setRepairEstimate(db.calculatePostalRepairTime(postalCode));
            }
        }
        catch(SQLException e){
            throw new SQLException("SQL select from DistributionHubs, PostalCodes, PostalHubRelation tables failed!\nSource: hubRepair\nDetails: " + e.getMessage());
        }
    }



    void hubRepair(String hubIdentifier, String employeeId, float repairTime, boolean inService) throws Exception{
        if(hubIdentifier==null){
            throw new IllegalArgumentException("HubIdentifier is null! \nSource: hubRepair");
        }
        hubIdentifier = hubIdentifier.replaceAll("\\s+","");
        if(hubIdentifier.equals("")){
            throw new IllegalArgumentException("HubIdentifier is empty! \nSource: hubRepair");
        }
        hubIdentifier = hubIdentifier.toUpperCase();
        if(!distributionHubs.containsKey(hubIdentifier)){
            throw new IllegalArgumentException("HubIdentifier does not exist (has not been added)! \nSource: hubRepair");
        }
        if(distributionHubs.get(hubIdentifier).getInService()){
            throw new IllegalArgumentException("This hub is already in service (invalid)! \nSource: hubRepair");
        }
        if(employeeId==null){
            throw new IllegalArgumentException("EmployeeId is null! \nSource: hubRepair");
        }
        employeeId = employeeId.replaceAll("\\s+", "");
        if(employeeId.equals("")){
            throw new IllegalArgumentException("EmployeeId is empty! \nSource: hubRepair");
        }
        if(repairTime<0){
            throw new IllegalArgumentException("RepairTime is negative! \nSource: hubRepair");
        }
        try{
            db.updateRepairLog(employeeId, hubIdentifier, repairTime, inService);
        }
        catch(SQLException e){
            throw new SQLException("SQL insert on RepairLog table failed!\nSource: hubRepair \nDetails: " + e.getMessage());
        }
        if(inService){
            HubImpact hub = distributionHubs.get(hubIdentifier);
            hub.setRepairTime(0);
            hub.setInService(true);
            hub.setImpact(0);
            hub.setPopulationEffected(0);
            try{
                db.applyHubRepairToDB(hubIdentifier, 0, true);
            }
            catch(SQLException e){
                throw new SQLException("SQL Update on DistributionHubs table failed!\nSource: hubRepair \nDetails: " + e.getMessage());
            }
            try{
                //hub.setPopulationEffected(db.calculatePopulationEffected(hub.getHubId(), hub.getServicedAreas()));
                //hub.setImpact(((float) hub.getPopulationEffected()) / hub.getRepairTime());
                Iterator itr = hub.getServicedAreas().iterator();
                while(itr.hasNext()){
                    String postalCode = (String) itr.next();
                    postalCodes.get(postalCode).setRepairEstimate(db.calculatePostalRepairTime(postalCode));
                }
            }
            catch(SQLException e){
                throw new SQLException("SQL select from DistributionHubs, PostalCodes, PostalHubRelation tables failed!\nSource: hubRepair\nDetails: " + e.getMessage());
            }
        }
        else{
            HubImpact hub = distributionHubs.get(hubIdentifier);
            //DOCUMENT THIS: if repairTime >= repairEstimate, dont change repair estimate? (what other way can we work around this???)
            if(repairTime<hub.getRepairTime()){
                hub.setRepairTime(hub.getRepairTime() - repairTime);
            }
            try{
                db.applyHubRepairToDB(hubIdentifier, hub.getRepairTime(), false);
            }
            catch(SQLException e){
                throw new SQLException("SQL Update on DistributionHubs table failed!\nSource: hubRepair \nDetails: " + e.getMessage());
            }
            try{
                hub.setPopulationEffected(db.calculatePopulationEffected(hub.getHubId(), hub.getServicedAreas()));
                hub.setImpact(((float) hub.getPopulationEffected()) / hub.getRepairTime());
                Iterator itr = hub.getServicedAreas().iterator();
                while(itr.hasNext()){
                    String postalCode = (String) itr.next();
                    postalCodes.get(postalCode).setRepairEstimate(db.calculatePostalRepairTime(postalCode));
                }
            }
            catch(SQLException e){
                throw new SQLException("SQL select from DistributionHubs, PostalCodes, PostalHubRelation tables failed!\nSource: hubRepair\nDetails: " + e.getMessage());
            }
        }
    }


    int peopleOutOfService() throws SQLException{
        float totalPeopleOutOfService = 0;
        try{
            for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){
                float postalCodePopOutOfService = db.postalPopulationWithHubOutOfService(entry.getKey());
                totalPeopleOutOfService += entry.getValue().getPopulation() * postalCodePopOutOfService;
            }
        }
        catch(SQLException e){
            throw new SQLException("SQL join/select from PostalHubRelations and DistributionHubs failed!\nSource: peopleOutOfService\nDetails: " + e.getMessage());
        }
        try{
            totalPeopleOutOfService += db.postalPopulationWithoutHubOutOfService();
        }
        catch(SQLException e){
            throw new SQLException("SQL join/select from PostalCodes and PostalHubRelation failed!\nSource: peopleOutOfService\nDetails: " + e.getMessage());
        }
        int peopleOutOfService = (int) Math.ceil(totalPeopleOutOfService);
        return peopleOutOfService;
    }


    List<DamagedPostalCodes> mostDamagedPostalCodes(int limit) throws Exception{
        if(limit<1){
            throw new IllegalArgumentException("Limit is less than 1 (invalid)! \nSource: mostDamagedPostalCodes");
        }
        Map<String, Float> postalRepairTimes = new HashMap<>();
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){
            float repairEstimate = entry.getValue().getRepairEstimate();
            try{
                repairEstimate = db.calculatePostalRepairTime(entry.getKey());
                entry.getValue().setRepairEstimate(repairEstimate);
            }
            catch(SQLException e){
                throw new SQLException("SQL join/select from PostalHubRelations and DistributionHubs failed!\nSource: mostDamagedPostalCodes\nDetails: " + e.getMessage());
            }
            if(repairEstimate>0){
                postalRepairTimes.put(entry.getKey(), repairEstimate);
            }
        }
        if(postalRepairTimes.isEmpty()){
            return new ArrayList<>();
        }
        Map<String, Float> sortedPostalRepairTimes = sortMapByValue(postalRepairTimes);
        List<DamagedPostalCodes> mostDamagedPostalCodes = new ArrayList<>();
        int counter = 0;
        float valueAtLimit = -1;
        for(Map.Entry<String, Float> entry: sortedPostalRepairTimes.entrySet()){
            if(counter<limit-1){
                mostDamagedPostalCodes.add(postalCodes.get(entry.getKey()));
                counter++;
            }
            else if(counter==limit-1){
                mostDamagedPostalCodes.add(postalCodes.get(entry.getKey()));
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{
                if(entry.getValue()==valueAtLimit){
                    mostDamagedPostalCodes.add(postalCodes.get(entry.getKey()));
                }
                else{
                    break;
                }
            }
        }
        return mostDamagedPostalCodes;
    }


    private Map<String, Float> sortMapByValue(Map<String, Float> unsortedMap){
        Map<String, Float> sortedMap = new LinkedHashMap<>();   //linkedHashMap to retain insertion order
        List<Float> sortedValues = new ArrayList<>();
        sortedValues.addAll(unsortedMap.values());
        Collections.sort(sortedValues);
        Collections.reverse(sortedValues);
        for(int i=0; i<sortedValues.size(); i++){
            for(Map.Entry<String, Float> entry: unsortedMap.entrySet()){
                if(entry.getValue() == sortedValues.get(i) && !sortedMap.containsKey(entry.getKey())){
                    sortedMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return sortedMap;
    }


    List<HubImpact> fixOrder(int limit) throws Exception{
        if(limit<1){
            throw new IllegalArgumentException("Limit is less than 1 (invalid)! \nSource: fixOrder");
        }
        Map<String, Float> hubImpacts = new HashMap<>();
        for(Map.Entry<String, HubImpact> entry: distributionHubs.entrySet()){
            if(!entry.getValue().getInService()){
                try{
                    float populationEffected = db.calculatePopulationEffected(entry.getKey(), entry.getValue().getServicedAreas());
                    float impact = populationEffected / entry.getValue().getRepairTime();
                    entry.getValue().setImpact(impact);
                    entry.getValue().setPopulationEffected(populationEffected);
                    hubImpacts.put(entry.getKey(), impact);
                }
                catch(SQLException e){
                    throw new SQLException("SQL join/select from PostalHubRelations and DistributionHubs failed!\nSource: fixOrder\nDetails: " + e.getMessage());
                }
            }
        }
        if(hubImpacts.isEmpty()){
            return new ArrayList<>();
        }
        List<HubImpact> fixOrder = new ArrayList<>();
        Map<String, Float> sortedHubImpacts = sortMapByValue(hubImpacts);
        int counter = 0;
        float valueAtLimit = -1;
        for(Map.Entry<String, Float> entry: sortedHubImpacts.entrySet()){
            if(counter<limit-1){
                fixOrder.add(distributionHubs.get(entry.getKey()));
                counter++;
            }
            else if(counter==limit-1){
                fixOrder.add(distributionHubs.get(entry.getKey()));
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{
                if(entry.getValue()==valueAtLimit){
                    fixOrder.add(distributionHubs.get(entry.getKey()));
                }
                else{
                    break;
                }
            }
        }
        return fixOrder;
    }


    List<String> underservedPostalByPopulation(int limit) throws Exception{
        if(limit<1){
            throw new IllegalArgumentException("Limit is less than 1 (invalid)! \nSource: underservedPostalByPopulation");
        }
        List<String> underservedPostals = new ArrayList<>();
        Map<String, Float> postalPopHubs = new HashMap<>();
        List<String> noServicePostals = new ArrayList<>();
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){
            float population = (float) entry.getValue().getPopulation();
            try{
                float postalHubs = db.getNumberOfPostalHubs(entry.getKey());
                if(postalHubs==0){
                    noServicePostals.add(entry.getKey());
                    continue;
                }
                float hubsPerCapita = population / postalHubs;
                postalPopHubs.put(entry.getKey(), hubsPerCapita);
            }
            catch(SQLException e){
                throw new SQLException("SQL select from PostalHubRelations failed! \nSource: underservedPostalByPopulation\nDetails: " + e.getMessage());
            }
        }
        if(postalPopHubs.isEmpty()){
            return underservedPostals;
        }
        postalPopHubs = sortMapByValue(postalPopHubs);
        float valueAtLimit = -1;
        int counter = 0;
        for(int i=0; i<noServicePostals.size(); i++){
            underservedPostals.add(noServicePostals.get(i));
            counter++;
        }
        for(Map.Entry<String, Float> entry: postalPopHubs.entrySet()){
            if(counter<limit-1){
                underservedPostals.add(entry.getKey());
                counter++;
            }
            else if(counter==limit-1){
                underservedPostals.add(entry.getKey());
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{
                if(entry.getValue()==valueAtLimit){
                    underservedPostals.add(entry.getKey());
                }
                else{
                    break;
                }
            }
        }
        return underservedPostals;
    }


    List<String> underservedPostalByArea(int limit) throws Exception{
        if(limit<1){
            throw new IllegalArgumentException("Limit is less than 1 (invalid)! \nSource: underservedPostalByArea");
        }
        List<String> underservedPostals = new ArrayList<>();
        Map<String, Float> postalAreaHubs = new HashMap<>();
        List<String> noServicePostals = new ArrayList<>();
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){
            float area = (float) entry.getValue().getArea();
            try{
                float postalHubs = db.getNumberOfPostalHubs(entry.getKey());
                if(postalHubs==0){
                    noServicePostals.add(entry.getKey());
                    continue;
                }
                float hubsPerArea = area / postalHubs;
                postalAreaHubs.put(entry.getKey(), hubsPerArea);
            }
            catch(SQLException e){
                throw new SQLException("SQL select from PostalHubRelations failed! \nSource: underservedPostalByArea\nDetails: " + e.getMessage());
            }
        }
        if(postalAreaHubs.isEmpty() && noServicePostals.isEmpty()){
            return underservedPostals;
        }
        postalAreaHubs = sortMapByValue(postalAreaHubs);
        float valueAtLimit = -1;
        int counter = 0;
        for(int i=0; i<noServicePostals.size(); i++){
            underservedPostals.add(noServicePostals.get(i));
            counter++;
        }
        for(Map.Entry<String, Float> entry: postalAreaHubs.entrySet()){
            if(counter<limit-1){
                underservedPostals.add(entry.getKey());
                counter++;
            }
            else if(counter==limit-1){
                underservedPostals.add(entry.getKey());
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{
                if(entry.getValue()==valueAtLimit){
                    underservedPostals.add(entry.getKey());
                }
                else{
                    break;
                }
            }
        }
        return underservedPostals;
    }



    List<Integer> rateOfServiceRestoration(float increment) throws Exception{
        if(increment<=0){
            throw new IllegalArgumentException("Increment is zero or negative (invalid)!\nSource: rateOfServiceRestoration");
        }
        if(increment>1){
            throw new IllegalArgumentException("Increment is greater than 1.0 (invalid)!\nSource: rateOfServiceRestoration");
        }
        if(increment<0.001){
            throw new IllegalArgumentException("Increment is too small (less than 0.001) [Invalid: granularity issue]!\nSource: rateOfServiceRestoration");
        }
        if(distributionHubs.isEmpty()){
            throw new IllegalArgumentException("No distribution hubs exists (invalid) [will create infinite list of zeroes]!\nSource: rateOfServiceRestoration");
        }
        List<Integer> rateOfRestoration = new ArrayList<>();
        float size = 0;
        while(size<=1){
            rateOfRestoration.add(0);
            if(size==1){
                break;
            }
            size += increment;
            if(size>1){
                size = 1;
            }
        }
        List<HubImpact> fixOrder = fixOrder(distributionHubs.size());
        for(int i=fixOrder.size()-1; i>=0; i--){
            if(fixOrder.get(i).getImpact()==0){
                fixOrder.remove(i);
            }
            else{
                break;
            }
        }
        if(fixOrder.isEmpty()){
            return rateOfRestoration;
        }
        int totalPopulation = getTotalPopulation();
        float populationOutOfServiceFloat = 0;
        try{
            for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){
                float postalCodePopOutOfService = db.postalPopulationWithHubOutOfService(entry.getKey());
                populationOutOfServiceFloat += entry.getValue().getPopulation() * postalCodePopOutOfService;
            }
        }
        catch(SQLException e){
            throw new SQLException("SQL join/select from PostalHubRelations and DistributionHubs failed!\nSource: peopleOutOfService\nDetails: " + e.getMessage());
        }
        int populationOutOfService = (int) Math.ceil(populationOutOfServiceFloat);
        if(populationOutOfService==0 /*|| totalPopulation==0*/){
            return rateOfRestoration;
        }
        rateOfRestoration.clear();
        float percentageOfInServicePop = (((float) totalPopulation)- ((float) populationOutOfService)) / ((float) totalPopulation);
        double alreadyHavePowerIncrements = Math.floor(percentageOfInServicePop/increment);
        float currIncrement = 0;
        for(int i=0; i<=alreadyHavePowerIncrements; i++){
            rateOfRestoration.add(0);
            currIncrement += increment;
        }
        float repairTimeElapsed = 0;
        if(currIncrement>1){
            currIncrement = 1;
        }
        for(int i=0; i<fixOrder.size(); i++){
            repairTimeElapsed += fixOrder.get(i).getRepairTime();
            percentageOfInServicePop += ((float) fixOrder.get(i).getPopulationEffected()) / ((float) totalPopulation);
            while(percentageOfInServicePop>=currIncrement){
                if(currIncrement==1){
                    rateOfRestoration.add((int) Math.ceil(repairTimeElapsed));
                    currIncrement = -1;
                    break;
                }
                rateOfRestoration.add((int) Math.ceil(repairTimeElapsed));
                if(currIncrement+increment>1){
                    currIncrement = 1;
                }
                else{
                    currIncrement += increment;
                }
            }
        }
        return rateOfRestoration;
    }


    private int getTotalPopulation(){
        int totalPopulation = 0;
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){
            for(Map.Entry<String, HubImpact> hub: distributionHubs.entrySet()){
                Set<String> servicedAreas = hub.getValue().getServicedAreas();
                Iterator itr = servicedAreas.iterator();
                boolean postalIsServiced = false;
                while(itr.hasNext()){
                    String postalCode = (String) itr.next();
                    if(postalCode.equals(entry.getKey())){
                        totalPopulation += entry.getValue().getPopulation();
                        postalIsServiced = true;
                        break;
                    }
                }
                if(postalIsServiced){
                    break;
                }
            }
            //totalPopulation += entry.getValue().getPopulation();
        }
        return totalPopulation;
    }



    List<HubImpact> repairPlan(String startHub, int maxDistance, float maxTime){
        return null;
    }

    /*private void synchronizeDB() throws SQLException{

    }*/
}