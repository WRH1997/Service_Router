import java.sql.*;
import java.util.*;
import java.io.*;


//this class acts as an interface between PowerService and the MySQL database. In other words, an object of this class provides
//PowerService with all database related functions which include fetching, inserting, and updating tables
public class Database {

    private String username;   //MySQL username (retrieved from credentials.prop)
    private String password;  //MySQL password (retrieved from credentials.prop)
    private String database;   //MySQL database [i.e., the schema we want to utilize such as "csci3901" or "alhindi"] (retrieved from credentials.prop)
    private Properties credentials;   //variable to store properties (.prop) file
    private String connectionURL;   //string to store SQL connection URL
    private String useDB;   //a string to store the "use <schema>" SQL command (i.e., "use alhindi")


    Database() throws Exception{
        credentials = new Properties();
        try{
            //read username, password, and database from properties file (credentials.prop)
            InputStream input = getClass().getResourceAsStream("credentials.prop");
            credentials.load(input);
            username = credentials.getProperty("username");
            password = credentials.getProperty("password");
            database = credentials.getProperty("database");
            useDB = "use " + database + ";";
            //set the SQL connection URL
            connectionURL = "jdbc:mysql://db.cs.dal.ca:3306?serverTimezone=UTC&useSSL=false";
        }
        catch(IOException e){
            throw new IOException("Error reading credentials file (prop file)!\nDetails: " + e.getMessage());
        }
        try{
            //verify that we can connect to the database using the credentials supplied
            //by running a simple "use <schema>" command (i.e., connect to database and execute "use alhindi")
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(connectionURL, username, password);
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw new SQLException("Database connection failed!\nDetails: " + e.getMessage());
        }
    }



    //this method returns a Connection object to the database. It is meant to reduce redundant code since each method needs to
    //establish a connection to the database
    private Connection getConnection() throws SQLException{
        Connection conn = null;
        try{
            conn = DriverManager.getConnection(connectionURL, username, password);
        }
        catch(SQLException e){
            throw new SQLException("Database connection failed!\nDetails: " + e.getMessage());
        }
        return conn;
    }




    //this method is invoked during PowerService's constructor and returns all the postal codes stored in the database's PostalCodes table
    List<DamagedPostalCodes> getPostalCodesFromDB() throws SQLException{
        List<DamagedPostalCodes> damagedPostalCodes = new ArrayList<>();    //list to store the postal codes that exist in the database's PostalCodes tables
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            ResultSet postals = statement.executeQuery("select * from PostalCodes;");
            //store each existing postal code in a DamagedPostalCodes object, and add that object to the list
            while(postals.next()){
                String postalCode = postals.getString("id");
                float repairEstimate = calculatePostalRepairTime(postalCode);
                damagedPostalCodes.add(new DamagedPostalCodes(postalCode, postals.getInt("population"), postals.getInt("area"), repairEstimate));
            }
            postals.close();
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
        return damagedPostalCodes;
    }




    //this method calculates a postal code's total repair time by cross-referencing between the PostalHubRelation and DistributionHubs tables.
    //it is used during various PowerService methods
    float calculatePostalRepairTime(String postalCode) throws SQLException{
        float postalRepairTime = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //get all the hubs that service this postal code
            ResultSet res = statement.executeQuery("select * from PostalHubRelation join DistributionHubs on PostalHubRelation.hubId=DistributionHubs.id where PostalHubRelation.postalId = '" + postalCode + "';");
            while(res.next()){   //for each of those hubs
                if(!res.getBoolean("inService")){   //the hub is downed (out of service)
                    postalRepairTime += res.getFloat("repairTime");   //add that hub's repair estimate to this postal code's total repair time
                }
            }
            res.close();
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
        return postalRepairTime;
    }




    //this method is invoked during PowerService's constructor and returns all the distribution hubs stored in the database's DistributionHubs table
    List<HubImpact> getDistributionHubsFromDB() throws SQLException{
        List distributionHubs = new ArrayList<>();   //list to store the distribution hubs that exist in the database's DistributionHubs tables
        try{
            Connection conn = getConnection();
            Statement statement1 = conn.createStatement();
            statement1.execute(useDB);
            //get all the stored distribution hubs
            ResultSet hubs = statement1.executeQuery("select * from DistributionHubs;");
            while(hubs.next()){   //for each existing distribution hub
                String hubId = hubs.getString("id");
                Set<String> servicedAreas = new HashSet<>();
                Statement statement2 = conn.createStatement();
                //get all the postal codes that this hub services
                ResultSet hubPostals = statement2.executeQuery("select postalId from PostalHubRelation where hubId = '" + hubId + "';");
                //add each serviced hub to this hub's servicedAreas set
                while(hubPostals.next()){
                    servicedAreas.add(hubPostals.getString("postalId"));
                }
                //check whether this hub is in service
                boolean inService = hubs.getBoolean("inService");
                //if this hub in service, then create a HubImpact object to store it, and add that object to the list
                if(inService){
                    distributionHubs.add(new HubImpact(hubId, new Point(hubs.getInt("x"), hubs.getInt("y")), servicedAreas, hubs.getFloat("repairTime"), inService, 0, 0));
                }
                //if this hub is not in service, then calculate the number of people effected by its outage and its impact. Then, create a HubImpact object to store this hub and add that object to the list
                else{
                    float effectedPopulation = calculatePopulationEffected(hubId, servicedAreas);
                    float repairTime = hubs.getFloat("repairTime");
                    float impact = effectedPopulation/repairTime;
                    distributionHubs.add(new HubImpact(hubId, new Point(hubs.getInt("x"), hubs.getInt("y")), servicedAreas, repairTime, inService, impact, effectedPopulation));
                }
                hubPostals.close();
                statement2.close();
            }
            hubs.close();
            statement1.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
        return distributionHubs;
    }




    //this method is used by various PowerService methods and by this class's getDistributionHubsFromDB method.
    //It calculates the total number of people affected by a hub's outage (i.e., for a given hub, how many people are out of power because of its outage)
    float calculatePopulationEffected(String hubId, Set<String> servicedAreas) throws SQLException{
        float populationEffected = 0;
        Iterator itr = servicedAreas.iterator();
        while(itr.hasNext()){    //iterate through this hub's serviced postal codes
            String postalCode = (String) itr.next();
            try{
                Connection conn = getConnection();
                Statement statement = conn.createStatement();
                statement.execute(useDB);
                //get the population of this postal code
                ResultSet postalPop = statement.executeQuery("select population from PostalCodes where id = '" + postalCode + "';");
                float postalPopulation = 0;
                while(postalPop.next()){
                    postalPopulation = (float) postalPop.getInt("population");
                }
                postalPop.close();
                //get the total number of hubs that service this postal code
                float postalHubs = getNumberOfPostalHubs(postalCode);
                //calculate the fraction of this postal code's population that is affected by this hub's outage
                populationEffected += postalPopulation * (1/postalHubs);
                postalPop.close();
                statement.close();
                conn.close();
            }
            catch(SQLException e){
                throw e;
            }
        }
        return populationEffected;
    }




    //this method finds and returns the total number of hubs that service a postal code.
    //It is used during PowerService's underservedPostalByPopulation, underservedPostalByArea, and this class's calculatePopulationEffected methods
    float getNumberOfPostalHubs(String postalCode) throws SQLException{
        float postalHubs = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //get the count of rows returned for this postal code in the PostalHubRelation table (where each entry is a unique relation between a postal code and a hub servicing it)
            ResultSet res = statement.executeQuery("select count(*) as hubs from PostalHubRelation where postalId = '" + postalCode + "';");
            while(res.next()){
                postalHubs = (float) res.getInt("hubs");
            }
            res.close();
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
        return postalHubs;
    }




    //method to add postal codes provided through the addPostalCode method to the database
    void addPostalCodeToDB(DamagedPostalCodes newPostalCode) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //simple SQL insertion of new postal code's data into PostalCodes table
            statement.execute("insert into PostalCodes values('" + newPostalCode.getPostalCodeId() + "', " + newPostalCode.getPopulation() + ", " + newPostalCode.getArea() + ");");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }




    //method to establish relationship between postal code and hub servicing it by inserting
    //the pairing into the PostalHubRelation many-to-many table
    //this method is used during addPostalCode and addDistributionHub
    void updatePostalHubRelation(String postalCode, String hubId) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //first, check if the relationship between the postal and hub already exists
            ResultSet res = statement.executeQuery("select * from PostalHubRelation where postalId='" + postalCode + "' and hubId='" + hubId + "';");
            //CITATION NOTE: I was unsure on how to check if a ResultSet is empty, so I referenced the following URL for the boolean statement [if(res.next()==false)]:
            //URL: https://javarevisited.blogspot.com/2016/10/how-to-check-if-resultset-is-empty-in-Java-JDBC.html#axzz7mL7lknva
            //Accessed: December 8, 2022
            if(res.next()==false){   //relationship does not already exist
                //so, insert a row to represent the relationship in the PostalHubRelation table
                statement.execute("insert into PostalHubRelation values('" + postalCode + "', '" + hubId + "');");
            }
            res.close();
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }




    //method to add a new distribution hub provided through the addDistributionHub method
    //to the database's DistributionHubs table
    void addDistributionHubToDB(HubImpact newHub) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //simple SQL insert of new hub's data into DistributionHubs table
            statement.execute("insert into DistributionHubs values('" + newHub.getHubId() + "', " + newHub.getLocation().getX() + ", " +newHub.getLocation().getY() + ", " + 0 + ", " + true + ");");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }



    //method to update a hub's damage as a result of hubDamage being called
    void updateHubDamage(String hubId, float repairTime) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //simple SQL update command to reflect changes in a hub's repair time and inService attributes in the DistributionHubs table
            statement.execute("update DistributionHubs set repairTIme = repairTime + " + repairTime + ", inService = false where id = '" + hubId + "';");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }




    //method to find the fraction of a postal code's hubs that are down (i.e., if a postal code has 4 hubs servicing it and 2 are down,
    //then this method returns 0.5). This method is used to calculate the number of people in a postal code that are out of power due to
    //hub outages in peopleOutOfService and rateOfRestoration
    float percentageOfPostalDamagedHubs(String postalCode) throws SQLException{
        int totalHubs = 0;
        int damagedHubs = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //join between PostalHubRelation and DistributionHubs on this postal code to get the information of each of the hubs that service this postal code
            ResultSet res = statement.executeQuery("select * from PostalHubRelation join DistributionHubs on PostalHubRelation.hubId=DistributionHubs.id where PostalHubRelation.postalId = '" + postalCode + "';");
            while(res.next()){   //iterate through the hubs that service this postal
                totalHubs++;
                boolean inService = res.getBoolean("inService");
                if(!inService){
                    damagedHubs++;
                }
            }
        }
        catch(SQLException e){
            throw e;
        }
        if(totalHubs==0){   //check if this postal is not being serviced by any hubs
            return 0;
        }
        //calculate the fraction of downed hubs servicing this postal code
        float downedHubPercentage = ((float) damagedHubs)/((float)totalHubs);
        return downedHubPercentage;
    }




    //method to find the total population that is out of service due to not being serviced by any hubs.
    //This method is needed due in specialized situations that arise from design decisions made during the implementation
    //of this program (see  external document for more detail)
    float postalPopulationWithoutHubOutOfService() throws SQLException{
        int populationOutOfService = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //left join PostalCodes and PostalHubRelation tables to find which postal code does not have corresponding entries in the PostalHubRelation table (i.e., it does not have any corresponding servicing hubs)
            ResultSet res = statement.executeQuery("select * from PostalCodes left join PostalHubRelation on PostalCodes.id = PostalHubRelation.postalId where hubId is null");
            //tally the populations of all such postal codes
            while(res.next()){
                populationOutOfService += res.getInt("population");
            }
        }
        catch(SQLException e){
            throw e;
        }
        return populationOutOfService;
    }




    //method to log a repair done by an employee during hubRepair into the RepairLog table
    void updateRepairLog(String employeeId, String hubId, float repairTime, boolean inService) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //simple SQL insert of new repair's information into the RepairLog table
            statement.execute("insert into RepairLog values (null, '" + employeeId + "', '" + hubId + "', " + repairTime + "," + inService + ");");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }




    //method to update a hub's status as a result of repairHub being called
    void applyHubRepairToDB(String hubId, float repairTime, boolean inService) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            //simple SQL update command to reflect changes to hub's repairTime and inService attributes in the DistributionHubs table
            statement.execute("update DistributionHubs set repairTime= " + repairTime + ", inService= " + inService + " where id='" + hubId + "';");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }
}