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
        while(itr.hasNext()){
            String postalCode = (String) itr.next();
            try{
                Connection conn = getConnection();
                Statement statement = conn.createStatement();
                statement.execute(useDB);
                ResultSet postalPop = statement.executeQuery("select population from PostalCodes where id = '" + postalCode + "';");
                float postalPopulation = 0;
                while(postalPop.next()){
                    postalPopulation = (float) postalPop.getInt("population");
                }
                postalPop.close();
                float postalHubs = getNumberOfPostalHubs(postalCode);
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


    float getNumberOfPostalHubs(String postalCode) throws SQLException{
        float postalHubs = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
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


    void addPostalCodeToDB(DamagedPostalCodes newPostalCode) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            statement.execute("insert into PostalCodes values('" + newPostalCode.getPostalCodeId() + "', " + newPostalCode.getPopulation() + ", " + newPostalCode.getArea() + ");");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }


    void updatePostalHubRelation(String postalCode, String hubId) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            ResultSet res = statement.executeQuery("select * from PostalHubRelation where postalId='" + postalCode + "' and hubId='" + hubId + "';");
            //https://javarevisited.blogspot.com/2016/10/how-to-check-if-resultset-is-empty-in-Java-JDBC.html#axzz7mL7lknva
            if(res.next()==false){
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


    void addDistributionHubToDB(HubImpact newHub) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            statement.execute("insert into DistributionHubs values('" + newHub.getHubId() + "', " + newHub.getLocation().getX() + ", " +newHub.getLocation().getY() + ", " + 0 + ", " + true + ");");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }


    void updateHubDamage(String hubId, float repairTime) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            statement.execute("update DistributionHubs set repairTIme = repairTime + " + repairTime + ", inService = false where id = '" + hubId + "';");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }



    float postalPopulationWithHubOutOfService(String postalCode) throws SQLException{
        int totalHubs = 0;
        int damagedHubs = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            ResultSet res = statement.executeQuery("select * from PostalHubRelation join DistributionHubs on PostalHubRelation.hubId=DistributionHubs.id where PostalHubRelation.postalId = '" + postalCode + "';");
            while(res.next()){
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
        if(totalHubs==0){
            return 0;
        }
        float downedHubPercentage = ((float) damagedHubs)/((float)totalHubs);
        return downedHubPercentage;
    }


    float postalPopulationWithoutHubOutOfService() throws SQLException{
        int populationOutOfService = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            ResultSet res = statement.executeQuery("select * from PostalCodes left join PostalHubRelation on PostalCodes.id = PostalHubRelation.postalId where hubId is null");
            while(res.next()){
                populationOutOfService += res.getInt("population");
            }
        }
        catch(SQLException e){
            throw e;
        }
        return populationOutOfService;
    }

    void updateRepairLog(String employeeId, String hubId, float repairTime, boolean inService) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            statement.execute("insert into RepairLog values (null, '" + employeeId + "', '" + hubId + "', " + repairTime + "," + inService + ");");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }


    void applyHubRepairToDB(String hubId, float repairTime, boolean inService) throws SQLException{
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            statement.execute("update DistributionHubs set repairTime= " + repairTime + ", inService= " + inService + " where id='" + hubId + "';");
            statement.close();
            conn.close();
        }
        catch(SQLException e){
            throw e;
        }
    }
}