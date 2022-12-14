import java.io.IOException;
import java.sql.*;
import java.util.*;

public class PowerService{

    private Map<String, DamagedPostalCodes> postalCodes;   //map to store all postal codes as String-DamagedPostalCodes pairs where the String key is the postal code identifier
    private Map<String, HubImpact> distributionHubs;   //map to store all distribution hubs as String-HubImpact pairs where the string key is the hub's identifier
    private Database db;   //a Database interface class object that is used to access the SQL database


    //the PowerService constructor accesses the database and populates the postalCodes and distributionHubs map with the
    //existing postal codes and distribution hubs that are stored in the database
    PowerService() throws Exception{
        //first, check if we can access the SQL database
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
        //load in the existing postal codes stored in the database into the postalCodes map
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
        //load in the existing distribution hubs stored in the database into the distributionHubs map
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
        //CITATION NOTE: I referenced the following URL for the regex syntax to remove all empty spaces in a string
        //URL:https://stackoverflow.com/questions/5455794/removing-whitespace-from-strings-in-java
        //Accessed: December 7, 2022.
        postalCode = postalCode.replaceAll("\\s+","");    //standardize postal codes by eliminating all empty spaces
        if(postalCode.equals("")){
            return false;
        }
        postalCode = postalCode.toUpperCase();     //standardize postal codes by converting them to uppercase
        if(!postalCodeIsValidPattern(postalCode)){    //check if the postalCode conforms to the postal code format (i.e., letter-number-letter-number....)
            return false;
        }
        if(postalCodes.containsKey(postalCode)){   //check if the postalCode already exists (duplicate postals are invalid)
            return false;
        }
        if(population<0){
            return false;
        }
        if(area<1){
            return false;
        }
        //create a new DamagedPostalCodes object to store this postal code and pass it to the database object to insert it into the PostalCodes table
        DamagedPostalCodes newPostalCode = new DamagedPostalCodes(postalCode, population, area, 0);
        try{
            db.addPostalCodeToDB(newPostalCode);
        }
        catch(SQLException e){
            return false;
        }
        try{
            updatePostalHubRelation(postalCode);    //update the PostalHubRelation bridge table to reflect any hubs that service this postal code (a hub that serviced this postal may have been added before this postal, so we need to check and update any relations in the PostalHubRelation table accordingly)
            newPostalCode.setRepairEstimate(db.calculatePostalRepairTime(postalCode));    //calculate the total repair time needed (if any) to restore power to all this postal's population, and set the DamagedPostalCodes object's repairTime attribute to that calculated value
        }
        catch(SQLException e){
            return false;
        }
        postalCodes.put(newPostalCode.getPostalCodeId(), newPostalCode);   //add this new postal code to the postalCodes map
        return true;
    }




    //this method is used during addPostalCode to check whether a postal code identifier conforms to
    //the postal code format (i.e., letter-number-letter-number...)
    private boolean postalCodeIsValidPattern(String postalCode){
        int counter = 0;
        //iterate through the postal code string and check that each even index is a letter, and each odd index is a number (0 is even)
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




    //method used during addPostalCode and addDistributionHub to update the PostalHubRelation bridge table
    //to connect postal codes to the hubs that service them (many-to-many relationship)
    private void updatePostalHubRelation(String postalCode) throws SQLException{
        for(Map.Entry<String, HubImpact> entry: distributionHubs.entrySet()){    //iterate through each hub stored in the distributionHubs map
            if(entry.getValue().getServicedAreas().contains(postalCode)){    //iterate through each postal code in that hub's servicedArea set
                try{
                    db.updatePostalHubRelation(postalCode, entry.getKey());   //connect this hub to this postal code (if it is not already connected) by inserting the corresponding row into the PostalHubRelation table
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
        //CITATION NOTE: I referenced the following URL for the regex syntax to remove all empty spaces in a string
        //URL:https://stackoverflow.com/questions/5455794/removing-whitespace-from-strings-in-java
        //Accessed: December 7, 2022.
        hubIdentifier = hubIdentifier.replaceAll("\\s+","");    //standardize hub ids by removing all empty spaces
        if(hubIdentifier.equals("")){
            return false;
        }
        hubIdentifier = hubIdentifier.toUpperCase();    //standardize hub ids by converting them to uppercase
        if(distributionHubs.containsKey(hubIdentifier)){    //check if hub already exists (duplicates are invalid)
            return false;
        }
        if(location==null){
            return false;
        }
        if(hubExistsInLocation(location)){    //check if a hub already exists at the coordinates supplied in the location argument (no two hubs can have the same location)
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
            area = area.replaceAll("\\s+","");    //standardize postal codes by eliminating all empty spaces
            if(area.equals("")){
                return false;
            }
            area = area.toUpperCase();    //standardize postal codes by converting them to uppercase
            if(!postalCodeIsValidPattern(area)){
                return false;
            }
            servicedPostalCodes.add(area);
        }
        //create a HubImpact object to store this hub's information, and pass it to the database interface object to insert the hub's data into the DistributionHubs table
        HubImpact newHub = new HubImpact(hubIdentifier, location, servicedPostalCodes, 0, true, 0, 0);
        try{
            db.addDistributionHubToDB(newHub);
        }
        catch(SQLException e){
            return false;
        }
        //for each of the postal codes this hub services, add a row to reflect that relationship in the PostalHubRelation table
        //note that a row is not added if the postal code does not exist, but will be added if/when that postal code is added later on
        try{
            Iterator itr2 = servicedPostalCodes.iterator();
            while(itr2.hasNext()){
                String postalCode = (String) itr2.next();
                db.updatePostalHubRelation(postalCode, hubIdentifier);
            }
        }
        catch(SQLException e){
            return false;
        }
        distributionHubs.put(hubIdentifier, newHub);   //add the new hub to the distributionHubs map
        return true;
    }



    //this method is used during addDistributionHub. It checks whether a hub already exists in the location
    //of the new hub that trying to be added.
    private boolean hubExistsInLocation(Point location){   //The location parameter is the location of the new hub
        for(Map.Entry<String, HubImpact> entry: distributionHubs.entrySet()){   //iterate through the existing hubs
            if(entry.getValue().getLocation().compare(location)){      //check if the new hub's location has the same coordinates as this existing hub's location
                return true;
            }
        }
        return false;
    }




    void hubDamage(String hubIdentifier, float repairEstimate) throws Exception{
        if(hubIdentifier==null){
            throw new IllegalArgumentException("HubIdentifier is null! \nSource: hubDamage");
        }
        //CITATION NOTE: I referenced the following URL for the regex syntax to remove all empty spaces in a string
        //URL:https://stackoverflow.com/questions/5455794/removing-whitespace-from-strings-in-java
        //Accessed: December 7, 2022.
        hubIdentifier = hubIdentifier.replaceAll("\\s+","");   //convert hub id into the standard format (no spaces, all uppercase)
        if(hubIdentifier.equals("")){
            throw new IllegalArgumentException("HubIdentifier is empty! \nSource: hubDamage");
        }
        hubIdentifier = hubIdentifier.toUpperCase();   //convert hub id into the standard format (no spaces, all uppercase)
        if(!distributionHubs.containsKey(hubIdentifier)){    //check that the hub being reported exists
            throw new IllegalArgumentException("HubIdentifier does not exist (has not been added)! \nSource: hubDamage");
        }
        if(repairEstimate<=0){
            throw new IllegalArgumentException("RepairEstimate is zero or negative (invalid)! \nSource: hubDamage");
        }
        HubImpact hub = distributionHubs.get(hubIdentifier);
        hub.setRepairTime(hub.getRepairTime() + repairEstimate);   //increment the hub's repairTime attribute according to the repairTime being reported
        hub.setInService(false);   //set its inService to false to indicate it is offline.
        try{
            db.updateHubDamage(hubIdentifier, repairEstimate);   //update the DistributionHubs table to reflect this hub's damage
        }
        catch(SQLException e){
            throw new SQLException("SQL update to DistributionHubs table failed!\nSource: hubDamage\nDetails: " + e.getMessage());
        }
        try{
            hub.setPopulationEffected(db.calculatePopulationEffected(hub.getHubId(), hub.getServicedAreas()));   //calculate and set the total population that are effected by this hub's outage
            hub.setImpact(((float) hub.getPopulationEffected()) / hub.getRepairTime());   //calculate and set the hub's impact (significance) as the total amount of people affected by the hub's outage divided by its estimated repair time
            //now, we need to update each of this hub's serviced areas to reflect the new repairTime needed for all a postal
            //code's population to regain power
            Iterator itr = hub.getServicedAreas().iterator();
            while(itr.hasNext()){
                String postalCode = (String) itr.next();
                postalCodes.get(postalCode).setRepairEstimate(db.calculatePostalRepairTime(postalCode));   //recalculate and set postal code's total repair time (i.e., based on this hub and other hubs servicing it)
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
        //CITATION NOTE: I referenced the following URL for the regex syntax to remove all empty spaces in a string
        //URL:https://stackoverflow.com/questions/5455794/removing-whitespace-from-strings-in-java
        //Accessed: December 7, 2022.
        hubIdentifier = hubIdentifier.replaceAll("\\s+","");    //convert hub id into the standard format (no spaces, all uppercase)
        if(hubIdentifier.equals("")){
            throw new IllegalArgumentException("HubIdentifier is empty! \nSource: hubRepair");
        }
        hubIdentifier = hubIdentifier.toUpperCase();    //convert hub id into the standard format (no spaces, all uppercase)
        if(!distributionHubs.containsKey(hubIdentifier)){   //check if hub being repaired exists
            throw new IllegalArgumentException("HubIdentifier does not exist (has not been added)! \nSource: hubRepair");
        }
        if(distributionHubs.get(hubIdentifier).getInService()){    //check if the hub being repaired is already online (in service)
            throw new IllegalArgumentException("This hub is already in service (invalid)! \nSource: hubRepair");
        }
        if(employeeId==null){
            throw new IllegalArgumentException("EmployeeId is null! \nSource: hubRepair");
        }
        employeeId = employeeId.replaceAll("\\s+", "");    //standardize employee ids by eliminating empty spaces
        if(employeeId.equals("")){
            throw new IllegalArgumentException("EmployeeId is empty! \nSource: hubRepair");
        }
        if(repairTime<0){
            throw new IllegalArgumentException("RepairTime is negative! \nSource: hubRepair");
        }
        try{
            //insert row into RepairLog table to store this repair being performed
            db.updateRepairLog(employeeId, hubIdentifier, repairTime, inService);
        }
        catch(SQLException e){
            throw new SQLException("SQL insert on RepairLog table failed!\nSource: hubRepair \nDetails: " + e.getMessage());
        }
        if(inService){   //the repair results in the hub being online
            //set the hub estimated repair time, impact, population effected, and in service values to reflect that it is now online (regardless of repair time done)
            HubImpact hub = distributionHubs.get(hubIdentifier);
            hub.setRepairTime(0);
            hub.setInService(true);
            hub.setImpact(0);
            hub.setPopulationEffected(0);
            try{
                //update DistributionHubs table to reflect that this hub is now back online
                db.applyHubRepairToDB(hubIdentifier, 0, true);
            }
            catch(SQLException e){
                throw new SQLException("SQL Update on DistributionHubs table failed!\nSource: hubRepair \nDetails: " + e.getMessage());
            }
            try{
                //update each of the postal codes serviced by the hub that was repaired to reflect the change in the total
                //repair time each postal code needs to restore power to all its population
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
        else{   //the repair does not result in hub being online (still offline)
            HubImpact hub = distributionHubs.get(hubIdentifier);
            //the hub's repair estimate is only changed if this repair's time is less than the original estimate.
            //In the case that the repair time is greater than or equal to the original estimate, then the hub's estimate is not changed
            //(see design decision in external documentation for more details)
            if(repairTime<hub.getRepairTime()){
                hub.setRepairTime(hub.getRepairTime() - repairTime);
            }
            try{
                //update the DistributionHubs table to reflect this repair
                db.applyHubRepairToDB(hubIdentifier, hub.getRepairTime(), false);
            }
            catch(SQLException e){
                throw new SQLException("SQL Update on DistributionHubs table failed!\nSource: hubRepair \nDetails: " + e.getMessage());
            }
            try{
                //recalculate and set this hub's impact (population effected by its outage and [that population/new repair time estimate]}
                hub.setPopulationEffected(db.calculatePopulationEffected(hub.getHubId(), hub.getServicedAreas()));
                hub.setImpact(((float) hub.getPopulationEffected()) / hub.getRepairTime());
                //update each of the postal codes serviced by the hub that was repaired to reflect the change in the total
                //repair time each postal code needs to restore power to all its population
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
        float totalPeopleOutOfService = 0;   //tally variable
        try{
            for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){   //iterate through the postalCodes map
                float postalCodePopOutOfService = db.percentageOfPostalDamagedHubs(entry.getKey());  //calculate this postal code's population without service
                totalPeopleOutOfService += entry.getValue().getPopulation() * postalCodePopOutOfService;    //increment the tally by that population
            }
            //note that these calculations only take into account those postal codes that are serviced by at least one hub
        }
        catch(SQLException e){
            throw new SQLException("SQL join/select from PostalHubRelations and DistributionHubs failed!\nSource: peopleOutOfService\nDetails: " + e.getMessage());
        }
        try{
            //calculate the total population of postal codes that are not serviced by any hubs
            //(i.e., out of service since they have no hubs) and add that to the tally
            totalPeopleOutOfService += db.postalPopulationWithoutHubOutOfService();
        }
        catch(SQLException e){
            throw new SQLException("SQL join/select from PostalCodes and PostalHubRelation failed!\nSource: peopleOutOfService\nDetails: " + e.getMessage());
        }
        int peopleOutOfService = (int) Math.ceil(totalPeopleOutOfService);   //round up the tally (i.e., 10.5 becomes 11)
        return peopleOutOfService;
    }



    List<DamagedPostalCodes> mostDamagedPostalCodes(int limit) throws Exception{
        if(limit<1){
            throw new IllegalArgumentException("Limit is less than 1 (invalid)! \nSource: mostDamagedPostalCodes");
        }
        Map<String, Float> postalRepairTimes = new HashMap<>();     //map to store each postal code and its corresponding total amount of repair time to regain full power
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){   //iterate through the postalCodes map
            float repairEstimate = entry.getValue().getRepairEstimate();
            try{
                repairEstimate = db.calculatePostalRepairTime(entry.getKey());  //calculate this postal code's total repair time needed to regain full power
                entry.getValue().setRepairEstimate(repairEstimate);
            }
            catch(SQLException e){
                throw new SQLException("SQL join/select from PostalHubRelations and DistributionHubs failed!\nSource: mostDamagedPostalCodes\nDetails: " + e.getMessage());
            }
            //add only the postal codes that are affected by at least one hub outage to the postalRepairTImes map
            //(this also means that postal codes that are not serviced by any hubs are excluded [see design decisions in external documentation for more details])
            if(repairEstimate>0){
                postalRepairTimes.put(entry.getKey(), repairEstimate);
            }
        }
        if(postalRepairTimes.isEmpty()){    //there are no postal codes that are effected by hub outages, so just return an empty list
            return new ArrayList<>();
        }
        Map<String, Float> sortedPostalRepairTimes = sortMapByValue(postalRepairTimes);   //sort the postalRepairTimes map by its values in descending order
        List<DamagedPostalCodes> mostDamagedPostalCodes = new ArrayList<>();
        int counter = 0;
        float valueAtLimit = -1;
        for(Map.Entry<String, Float> entry: sortedPostalRepairTimes.entrySet()){    //iterate through this sorted list and add postal codes the list of most DamagedPostalCodes
            if(counter<limit-1){    //this postal code is before the limit so just add it
                mostDamagedPostalCodes.add(postalCodes.get(entry.getKey()));
                counter++;
            }
            else if(counter==limit-1){    //this postal code is at the limit, so we add it to the list and same its value (its repairTime)
                mostDamagedPostalCodes.add(postalCodes.get(entry.getKey()));
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{   //postal codes in sorted map are passed the limit
                if(entry.getValue()==valueAtLimit){     //keep adding postal codes beyond the limit that tie with the postal code at the limit
                    mostDamagedPostalCodes.add(postalCodes.get(entry.getKey()));
                }
                else{
                    break;
                }
            }
        }
        return mostDamagedPostalCodes;
    }



    //this sorts a map by its values in descending order and is used during
    //mostDamagedPostalCodes, fixOrder, underservedPostalByPopulation, and underservedPostalByArea
    private Map<String, Float> sortMapByValue(Map<String, Float> unsortedMap){
        Map<String, Float> sortedMap = new LinkedHashMap<>();   //map to store entries that are sorted by their values (linkedHashMap to retain insertion order)
        //add all the unsorted map's values to a list and then sort that list in descending order
        List<Float> sortedValues = new ArrayList<>();
        sortedValues.addAll(unsortedMap.values());
        Collections.sort(sortedValues);
        Collections.reverse(sortedValues);
        //match entries of the unsorted map with the sorted list's values, and those pairings to the sorted map
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
        Map<String, Float> hubImpacts = new HashMap<>();    //map to store each hub and its impact (population effected/repair time)
        for(Map.Entry<String, HubImpact> entry: distributionHubs.entrySet()){   //iterate through the distributionHubs map
            if(!entry.getValue().getInService()){    //take into account only the hubs that are experiencing an outage (not in service)
                try{
                    //calculate that hub's impact, then add the hub's identifier and impact to the hubImpact map
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
        if(hubImpacts.isEmpty()){   //no hubs are downed, so just return an empty list
            return new ArrayList<>();
        }
        List<HubImpact> fixOrder = new ArrayList<>();
        Map<String, Float> sortedHubImpacts = sortMapByValue(hubImpacts);  //sort the hubImpacts map by its values (impacts) in descending order
        int counter = 0;
        float valueAtLimit = -1;  //variable to store impact of hub at limit
        //iterate through the sorted map and add entries to the fixOrder list
        for(Map.Entry<String, Float> entry: sortedHubImpacts.entrySet()){
            if(counter<limit-1){   //hub is before list limit so just add it
                fixOrder.add(distributionHubs.get(entry.getKey()));
                counter++;
            }
            else if(counter==limit-1){   //hub is at the list limit so add it and store its impact value
                fixOrder.add(distributionHubs.get(entry.getKey()));
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{   //hub is passed the list limit
                if(entry.getValue()==valueAtLimit){   //add hubs passed the limit that tie in impact with the hub at the limit to the list
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
        Map<String, Float> postalPopHubs = new HashMap<>();    //map to store each postal code and its population per hub (population/number of servicing hubs)
        List<String> noServicePostals = new ArrayList<>();    //list to store postal codes that are not serviced by any hubs
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){    //iterate through the postalCodes map
            float population = (float) entry.getValue().getPopulation();
            try{
                float postalHubs = db.getNumberOfPostalHubs(entry.getKey());    //get the total number of hubs that service this postal code
                if(postalHubs==0){   //postal code is not serviced by any hubs, so add it to the dedicated list and move on to the next postal code
                    noServicePostals.add(entry.getKey());
                    continue;
                }
                //calculate this postal code's capitaPerHub and add its identifier and that calculated value to the postalPopHubs map
                float capitaPerHub = population / postalHubs;
                postalPopHubs.put(entry.getKey(), capitaPerHub);
            }
            catch(SQLException e){
                throw new SQLException("SQL select from PostalHubRelations failed! \nSource: underservedPostalByPopulation\nDetails: " + e.getMessage());
            }
        }
        if(postalPopHubs.isEmpty() && noServicePostals.isEmpty()){    //no postal codes exists, so just return an empty list
            return underservedPostals;
        }
        postalPopHubs = sortMapByValue(postalPopHubs);  //sort the postalPopHubs map by its values in descending order
        float valueAtLimit = -1;  //variable to store capitaPerHub of postal code at limit
        int counter = 0;
        //add all the postal codes that are not serviced by any hubs to the beginning of underservedPostals list
        //(this is because these postal codes are the most underserved by default regardless of their populations
        //and are all tied with a capitaPerHub of 0 meaning they will all be added to the list regardless of the limit argument)
        for(int i=0; i<noServicePostals.size(); i++){
            underservedPostals.add(noServicePostals.get(i));
            counter++;
        }
        for(Map.Entry<String, Float> entry: postalPopHubs.entrySet()){   //iterate through the sorted postalPopHubs map
            if(counter<limit-1){   //postal code is before list limit, so just add it
                underservedPostals.add(entry.getKey());
                counter++;
            }
            else if(counter==limit-1){   //postal code is at list limit, so add it and store its capitaPerHub value
                underservedPostals.add(entry.getKey());
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{   //postal code is passed list limit
                if(entry.getValue()==valueAtLimit){   //add postal codes that tie in capitaPerHub with the postal code at the limit
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
        Map<String, Float> postalAreaHubs = new HashMap<>();   //map to store each postal code and its area per hub (area/number of servicing hubs)
        List<String> noServicePostals = new ArrayList<>();   //list to store postal codes that are not serviced by any hubs
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){   //iterate through the postalCodes map
            float area = (float) entry.getValue().getArea();
            try{
                float postalHubs = db.getNumberOfPostalHubs(entry.getKey());   //get the total number of hubs that service this postal code
                if(postalHubs==0){   //postal code is not serviced by any hubs, so add it to the dedicated list and move on to the next postal code
                    noServicePostals.add(entry.getKey());
                    continue;
                }
                //calculate this postal code's areaPerHub and add its identifier and that calculated value to the postalAreaHubs map
                float areaPerHub = area / postalHubs;
                postalAreaHubs.put(entry.getKey(), areaPerHub);
            }
            catch(SQLException e){
                throw new SQLException("SQL select from PostalHubRelations failed! \nSource: underservedPostalByArea\nDetails: " + e.getMessage());
            }
        }
        if(postalAreaHubs.isEmpty() && noServicePostals.isEmpty()){   //no postal codes exists, so just return an empty list
            return underservedPostals;
        }
        postalAreaHubs = sortMapByValue(postalAreaHubs);    //sort the postalAreaHubs map by its values in descending order
        float valueAtLimit = -1;    //variable to store areaPerHub of postal code at limit
        int counter = 0;
        //add all the postal codes that are not serviced by any hubs to the beginning of underservedPostals list
        //(this is because these postal codes are the most underserved by default regardless of their areas
        //and are all tied with a areaPerHub of 0 meaning they will all be added to the list regardless of the limit argument)
        for(int i=0; i<noServicePostals.size(); i++){
            underservedPostals.add(noServicePostals.get(i));
            counter++;
        }
        for(Map.Entry<String, Float> entry: postalAreaHubs.entrySet()){   //iterate through the sorted postalAreaHubs map
            if(counter<limit-1){   //postal code is before list limit, so just add it
                underservedPostals.add(entry.getKey());
                counter++;
            }
            else if(counter==limit-1){    //postal code is at list limit, so add it and store its areaPerHub value
                underservedPostals.add(entry.getKey());
                counter++;
                valueAtLimit = entry.getValue();
            }
            else{   //postal code is passed list limit
                if(entry.getValue()==valueAtLimit){   //add postal codes that tie in areaPerHub with the postal code at the limit
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
            throw new IllegalArgumentException("No distribution hubs exists (invalid)!\nSource: rateOfServiceRestoration");
        }
        List<Integer> rateOfRestoration = new ArrayList<>();
        float listIncrement = 0;   //variable to store the current increment value
        int listSize = 0;   //variable to store rateOfRestoration's correct size based on the value of the increment argument
        while(listIncrement<=1){
            rateOfRestoration.add(0);  //add 0 to represent 0 hours to the list
            listSize++;
            if(listIncrement==1){   //current increment has reach 100%, so stop adding 0s to the list
                break;
            }
            listIncrement += increment;   //add "increment" value to the current increment variable
            if(listIncrement>1){   //current increment goes passed 100%, so set it back to 100% (this happens in cases where increment is 0.6 for example)
                listIncrement = 1;
            }
        }
        List<HubImpact> fixOrder = fixOrder(distributionHubs.size());   //get a list of hubs to fix sorted by their impacts in descending order
        //remove all hubs with an impact of 0 since they will have no effect on the percentage of population that are out of service
        for(int i=fixOrder.size()-1; i>=0; i--){
            if(fixOrder.get(i).getImpact()==0){
                fixOrder.remove(i);
            }
            else{
                break;
            }
        }
        if(fixOrder.isEmpty()){   //there are no hubs with an impact > 0 that need repair, so return the list of 0s
            return rateOfRestoration;
        }
        int totalPopulation = getTotalServicedPopulation();   //get the total amount of people being serviced by at least one hub (excluding postal codes that are not serviced by any hubs [see design decisions in external documentation ofr more details)]
        float populationOutOfServiceFloat = 0;
        try{   //calculate the total amount of people out of service due to a hub outage (i.e., excluding populations out of service due to not being serviced by any hubs in the first place)
            for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){
                float postalCodePopOutOfService = db.percentageOfPostalDamagedHubs(entry.getKey());
                populationOutOfServiceFloat += entry.getValue().getPopulation() * postalCodePopOutOfService;
            }
        }
        catch(SQLException e){
            throw new SQLException("SQL join/select from PostalHubRelations and DistributionHubs failed!\nSource: peopleOutOfService\nDetails: " + e.getMessage());
        }
        int populationOutOfService = (int) Math.ceil(populationOutOfServiceFloat);   //round up the population out of service
        if(populationOutOfService==0){   //no people are out of service due to a hub outage, so return the list of 0s
            return rateOfRestoration;
        }
        rateOfRestoration.clear();
        double percentageOfInServicePop = (((double) totalPopulation)- ((double) populationOutOfService)) / ((double) totalPopulation);
        double alreadyHavePowerIncrements = Math.floor(percentageOfInServicePop/increment);   //calculate how many "increments" of the population already have power
        float currIncrement = 0;   //variable to store the current increment we are operating on
        for(int i=0; i<=alreadyHavePowerIncrements; i++){   //add 0s to the list to indicate how many "increments" of the population already have power before performing any repairs
            rateOfRestoration.add(0);
            currIncrement += increment;   //update current increment
        }
        if(currIncrement>1){   //reset current increment to 1 if it has gone over 1 (i.e., when increment is 0.6)
            currIncrement = 1;
        }
        float repairTimeElapsed = 0;
        for(int i=0; i<fixOrder.size(); i++){   //iterate through the hubs that need repair (sorted by their impacts in descending order)
            repairTimeElapsed += fixOrder.get(i).getRepairTime();
            percentageOfInServicePop += ((double) fixOrder.get(i).getPopulationEffected()) / ((double) totalPopulation);   //calculate the new percentage of population in service after this hub is repaired
            while(percentageOfInServicePop>=currIncrement){   //add elapsedRepairTime(s) to the list to indicate how many new "increments" of the population are in service after this repair
                if(currIncrement==1){
                    rateOfRestoration.add((int) Math.ceil(repairTimeElapsed));
                    currIncrement = -1;
                    break;
                }
                rateOfRestoration.add((int) Math.ceil(repairTimeElapsed));
                if(currIncrement+increment>1){    //reset current increment to 1 if it has gone over 1 (i.e., when increment is 0.6)
                    currIncrement = 1;
                }
                else{
                    currIncrement += increment;
                }
            }
        }
        while(rateOfRestoration.size()<listSize){    //in case percentage of in service population is 0.9999999 but should be 1 (floating point calculation inaccuracy)
            rateOfRestoration.add((int) Math.ceil(repairTimeElapsed));
        }
        return rateOfRestoration;
    }




    //this method is used during rateOfRestoration. It calculates and returns the total population that is serviced by at least on hub
    private int getTotalServicedPopulation(){
        int totalPopulation = 0;
        for(Map.Entry<String, DamagedPostalCodes> entry: postalCodes.entrySet()){   //iterate through all the postal codes in the postalCodes map
            for(Map.Entry<String, HubImpact> hub: distributionHubs.entrySet()){   //iterate through all the distribution hubs in the distributionHubs map
                Set<String> servicedAreas = hub.getValue().getServicedAreas();
                Iterator itr = servicedAreas.iterator();
                boolean postalIsServiced = false;
                while(itr.hasNext()){   //iterate through all the postal codes stored in this hub's servicedAreas set
                    String postalCode = (String) itr.next();
                    if(postalCode.equals(entry.getKey())){   //this postal code is serviced by at least one hub (since it is in this hub's servicedAreas)
                        totalPopulation += entry.getValue().getPopulation();   //add this postal code's population to the total population
                        postalIsServiced = true;
                        break;
                    }
                }
                if(postalIsServiced){   //go to the next postal code
                    break;
                }
            }
        }
        return totalPopulation;
    }




    List<HubImpact> repairPlan(String startHub, int maxDistance, float maxTime) throws Exception{
        if(startHub==null){
            throw new IllegalArgumentException("startHub is null! \nSource: repairPlan");
        }
        startHub = startHub.replaceAll("\\s+","");  //convert starting hub id to standard format (no spaces, uppercase)
        if(startHub.equals("")){
            throw new IllegalArgumentException("startHub is empty String! \nSource: repairPlan");
        }
        startHub = startHub.toUpperCase();   //convert starting hub id to standard format (no spaces, uppercase)
        if(!distributionHubs.containsKey(startHub)){   //check if the starting hub exists
            throw new IllegalArgumentException("startHub does not exist (has not been added)!\nSource: repairPlan");
        }
        if(distributionHubs.get(startHub).getInService()){
            throw new IllegalArgumentException("startHub is already in service (invalid)!\nSource: repairPlan");
        }
        if(maxDistance<0){
            throw new IllegalArgumentException("maxDistance is negative (invalid)!\nSource: repairPlan");
        }
        if(maxTime<0){
            throw new IllegalArgumentException("maxTime is negative (invalid)!\nSource: repairPlan");
        }
        //get startHub and add it as the first hub in the repairPlan
        HubImpact firstHub = distributionHubs.get(startHub);
        List<HubImpact> repairPlan = new ArrayList<>();
        repairPlan.add(firstHub);
        //now, we calculate the hubs within <maxDistance> of startHub
        int firstHubX = firstHub.getLocation().getX();
        int firstHubY = firstHub.getLocation().getY();
        List<HubImpact> hubsInRange = new ArrayList<>();
        float highestImpact = -1;   //variable to store highest impact value of inRange hubs found so far
        HubImpact endHub = null;   //variable to store endHub based on the highest impact inRange hub found so far
        double furthestDistance = -1; //variable to store distance of endHub from startHub
        for(Map.Entry<String, HubImpact> hub: distributionHubs.entrySet()){   //iterate through the hubs in distributionHubs to find the hubs that are within <maxDistance> of startHub
            if(hub.getValue().getInService()){   //skips hubs already in service
                continue;
            }
            if(hub.getValue().getHubId().equals(firstHub.getHubId())){   //skip startHub
                continue;
            }
            HubImpact potentialHub = hub.getValue();
            int potentialHubX = potentialHub.getLocation().getX();
            int potentialHubY = potentialHub.getLocation().getY();
            //calculate distance of this hub from startHub using Pythagorean theorem (c^2 = a^2 + b^2)
            double distanceFromStart = Math.sqrt(Math.pow((potentialHubX - firstHubX), 2) + Math.pow((potentialHubY - firstHubY), 2));
            if(distanceFromStart<=(double)maxDistance){   //hub is within range
                hubsInRange.add(hub.getValue());
                //check if this hub has the highest impact so far. If it does, then it becomes the new endHub
                //OR, if this hub's impact is equal to the highest impact, then check if it is further than the currently designated endHub
                //since further distance is the tie-breaker between potential endHubs that are equal in impact (see design decisions in external documentation for more details)
                if(hub.getValue().getImpact()>highestImpact || (hub.getValue().getImpact()==highestImpact && distanceFromStart>furthestDistance)){
                    endHub = hub.getValue();
                    highestImpact = hub.getValue().getImpact();
                }
            }
        }
        //there are no hubs in range of startHub, so return list containing only startHub
        if(hubsInRange.isEmpty()){
            return repairPlan;
        }
        //now that we have identified all hubs in range and endHub, we need to find the intermediate hubs that are
        //between startHub and endHub (which can be repaired within <maxTime>)
        List<HubImpact> intermediateHubs = findIntermediateHubs(firstHub, endHub, hubsInRange, maxTime);
        //if not hubs reside between startHub and endHub, then return a list consists of only startHub, endHub
        if(intermediateHubs.isEmpty()){
            repairPlan.add(endHub);
            return repairPlan;
        }
        //in the case where startHub and endHub reside on the same x or y coordinate (forming a line between them),
        //then there is only one possible path. So, the repair plan would be startHub, all intermediate hubs on that line
        //between startHub and endHub, and endHub
        if(firstHubX==endHub.getLocation().getX() || firstHubY==endHub.getLocation().getY()){
            repairPlan = calculate1DRepairPlan(firstHub, endHub, intermediateHubs);
            return repairPlan;
        }
        //otherwise, a rectangle forms between startHub and endHub. So, we need to calculate the best possible valid repair plan path between the two
        else{
            //pass the relevant hub to a RepairPlanGrid object to create a 2d array to represent locational grid
            //and set start, end, and intermediate hubs on it
            RepairPlanGrid repairPlanGrid = new RepairPlanGrid(firstHub, endHub, intermediateHubs);
            List<List<HubImpact>> pathCombinations = new ArrayList<>();
            PowerSets powerSet = new PowerSets();
            //pass the set of intermediate hubs to a PowerSets object to calculate all possible subsets
            //For example, if [A1, B2] are the intermediate hubs, then their subsets (power-set) are [[], [A1], [B2], [A1,B2]]
            List<List<HubImpact>> hubSubsets = powerSet.calculateHubSubsets(intermediateHubs);
            HubPathCombinations combinations = new HubPathCombinations();
            //remove the empty set of the subsets (since power-sets always start with an empty set)
            hubSubsets.remove(0);
            //pass each of the intermediate hub subsets to a HubPathCombinations object to find all possible path combinations between the intermediate
            //hubs of a subset. For example, for the subset [A1, B2, C3], then the possible path combinations would be
            //[A1,B2,C3], [A1,C3,B2], [B2,A1,C3], [B2,C3,A1], [C3,A1,B2], and [C3,B2,A1]
            for(int i=0; i<hubSubsets.size(); i++){
                pathCombinations.addAll(combinations.getPathCombinations(hubSubsets.get(i)));
            }
            //clear the repairPlan and add startHub at the beginning of it
            repairPlan.clear();
            repairPlan.add(firstHub);
            //pass all possible path combinations to the RepairPlanGrid object to find the best (most impactful) valid path
            List<HubImpact> bestPossiblePath = repairPlanGrid.findBestRepairPath(pathCombinations, endHub);
            //add the best possible path to the repairPlan
            repairPlan.addAll(bestPossiblePath);
            //add endHub to the end of the repairPlan
            repairPlan.add(endHub);
            return repairPlan;
        }
    }



    //method used during repairPlan that finds all the hubs that reside between startHub and endHub (i.e., that lie inside the
    //rectangle formed between startHub and endHub)
    private List<HubImpact> findIntermediateHubs(HubImpact startHub, HubImpact endHub, List<HubImpact> hubsInRange, float maxTime){
        List<HubImpact> intermediateHubs = new ArrayList<>();
        //get the startHub and endHub's coordinates to form rectangle between them
        int startX = startHub.getLocation().getX();
        int startY = startHub.getLocation().getY();
        int endX = endHub.getLocation().getX();
        int endY = endHub.getLocation().getY();
        for(int i=0; i<hubsInRange.size(); i++){   //iterate through all hubs in range of startHub
            if(hubsInRange.get(i).getHubId().equals(endHub.getHubId())){   //skip endHub
                continue;
            }
            if(hubsInRange.get(i).getRepairTime()>maxTime){   //skip hubs that require repair time greater than <maxTime>
                continue;
            }
            //get this hub's coordinates
            int intermediateX = hubsInRange.get(i).getLocation().getX();
            int intermediateY = hubsInRange.get(i).getLocation().getY();
            //check whether this hub lies in the rectangle between startHub and endHub. But, to do that, we need to know whether that rectangle is in
            //quadrant 1, 2, 3, or 4 relative to startHub. For example, if startHub is at (0,0) and endHub is at (2,2). Then the rectangle between them
            //is situated in quadrant 1 of startHub where the diagonal line would be going towards the top right corner from startHub
            if(startX>endX){  //rectangle is in quadrant 2 or 3 (i.e., left of startHub)
                if(startY>endY){   //rectangle is in quadrant 3 (i.e., to the lower-left of startHub)
                    if(startX>=intermediateX && endX<=intermediateX){
                        if(startY>=intermediateY && endY<=intermediateY){
                            intermediateHubs.add(hubsInRange.get(i));   //this hub resides within the rectangle
                        }
                    }
                }
                else{   //rectangle is in quadrant 2 (i.e., to the upper-left of startHub)
                    if(startX>=intermediateX && endX<=intermediateX){
                        if(startY<=intermediateY && endY>=intermediateY){
                            intermediateHubs.add(hubsInRange.get(i));   //this hub resides within the rectangle
                        }
                    }
                }
            }
            else{   //rectangle is in quadrant 1 or 4 (i.e., to right of startHub)
                if(startY>endY){   //rectangle is in quadrant 4 (i.e., to the lower-right of startHub)
                    if(startX>=intermediateX && endX<=intermediateX){
                        if(startY>=intermediateY && endY<=intermediateY){
                            intermediateHubs.add(hubsInRange.get(i));   //this hub resides within the rectangle
                        }
                    }
                }
                else{   //rectangle is in quadrant 1 (i.e., to the upper-right of startHub)
                    if(startX<=intermediateX && endX>=intermediateX){
                        if(startY<=intermediateY && endY>=intermediateY){
                            intermediateHubs.add(hubsInRange.get(i));   //this hub resides within the rectangle
                        }
                    }
                }
            }
        }
        return intermediateHubs;
    }



    //method used during repairPlan when the startHub and endHub have the same x or y coordinate (forming a line between the two)
    //thus, there can only be one possible path (i.e., that line) and this method calculates and returns the repairPlan that corresponds
    //to that line (i.e., startHub, intermediate hubs on that line in order, endHub)
    private List<HubImpact> calculate1DRepairPlan(HubImpact startHub, HubImpact endHub, List<HubImpact> intermediateHubs){
        //first, we need to know whether startHub and endHub lie on the same x or y coordinate
        boolean xIsEqual = false;
        if(startHub.getLocation().getX()==endHub.getLocation().getX()){
            xIsEqual = true;
        }
        List<Integer> intermediateCoordinates = new ArrayList<>();
        //then, we add all the intermediate hub's x or y coordinates to a list depending on whether startHub and endHub share the same x or y coordinate
        for(int i=0; i<intermediateHubs.size(); i++){
            if(xIsEqual){   //startHub and endHub share the same x coordinate
                intermediateCoordinates.add(intermediateHubs.get(i).getLocation().getY());   //add all intermediate hub y coordinates to list
            }
            else{   //startHub and endHub share the same y coordinate
                intermediateCoordinates.add(intermediateHubs.get(i).getLocation().getX());   //add all intermediate hub x coordinates to list
            }
        }
        //now, we sort the list of intermediate hub x or y coordinates in ascending order
        Collections.sort(intermediateCoordinates);
        //however, now we may need to reverse the order of the list to descending order if the path between startHub and endHub
        //goes backwards horizontally or downwards vertically
        //For example, if startHub is (0,0) and endHub is (0,-6), then the list of intermediate hub y coordinates needs to be reversed to descending order
        //since the path goes downwards from startHub towards endHub
        if(xIsEqual && (startHub.getLocation().getX()>endHub.getLocation().getX())){
            Collections.reverse(intermediateCoordinates);
        }
        else if(!xIsEqual && (startHub.getLocation().getY()>endHub.getLocation().getY())){
            Collections.reverse(intermediateCoordinates);
        }
        //create a list for repairPlan and store startHub at the beginning
        List<HubImpact> repairPlan = new ArrayList<>();
        repairPlan.add(startHub);
        //iterate through the sorted list of intermediate hub x or y coordinates
        for(int i=0; i<intermediateCoordinates.size(); i++){
            //match this coordinate with its intermediate hub and add it to the repairPlan list
            for(int k=0; k<intermediateHubs.size(); k++){
                if(!xIsEqual){
                    if(intermediateHubs.get(k).getLocation().getX()==intermediateCoordinates.get(i)){
                        repairPlan.add(intermediateHubs.get(k));
                    }
                }
                else{
                    if(intermediateHubs.get(k).getLocation().getY()==intermediateCoordinates.get(i)){
                        repairPlan.add(intermediateHubs.get(k));
                    }
                }
            }
        }
        //finally, add endHub to the end of that list
        repairPlan.add(endHub);
        return repairPlan;
    }
}