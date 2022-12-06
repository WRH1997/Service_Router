import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

public class Database {

    private String username;
    private String password;
    private String database;
    private Properties credentials;
    private String connectionURL;
    private String useDB;

    Database() throws Exception{
        credentials = new Properties();
        try{
            InputStream input = new FileInputStream("src/credentials.prop");
            credentials.load(input);
            username = credentials.getProperty("username");
            password = credentials.getProperty("password");
            database = credentials.getProperty("database");
            connectionURL = "jdbc:mysql://db.cs.dal.ca:3306?serverTimezone=UTC&useSSL=false";
            useDB = "use " + database + ";";
        }
        catch(IOException e){
            throw new IOException("Error reading credentials file (prop file)!\nDetails: " + e.getMessage());
        }
        try{
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


    List<DamagedPostalCodes> getPostalCodesFromDB() throws SQLException{
        List damagedPostalCodes = new ArrayList<>();
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            ResultSet postals = statement.executeQuery("select * from PostalCodes;");
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


    float calculatePostalRepairTime(String postalCode) throws SQLException{
        float postalRepairTime = 0;
        try{
            Connection conn = getConnection();
            Statement statement = conn.createStatement();
            statement.execute(useDB);
            ResultSet res = statement.executeQuery("select * from PostalHubRelation join DistributionHubs on PostalHubRelation.hubId=DistributionHubs.id where PostalHubRelation.postalId = '" + postalCode + "';");
            while(res.next()){
                if(!res.getBoolean("inService")){
                    postalRepairTime += res.getFloat("repairTime");
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


    List<HubImpact> getDistributionHubsFromDB() throws SQLException{
        List distributionHubs = new ArrayList<>();
        try{
            Connection conn = getConnection();
            Statement statement1 = conn.createStatement();
            statement1.execute(useDB);
            ResultSet hubs = statement1.executeQuery("select * from DistributionHubs;");
            while(hubs.next()){
                String hubId = hubs.getString("id");
                Set<String> servicedAreas = new HashSet<>();
                Statement statement2 = conn.createStatement();
                ResultSet hubPostals = statement2.executeQuery("select postalId from PostalHubRelation where hubId = '" + hubId + "';");
                while(hubPostals.next()){
                    servicedAreas.add(hubPostals.getString("postalId"));
                }
                boolean inService = hubs.getBoolean("inService");
                if(inService){
                    distributionHubs.add(new HubImpact(hubId, new Point(hubs.getInt("x"), hubs.getInt("y")), servicedAreas, hubs.getFloat("repairTime"), inService, 0, 0));
                }
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


    /*float calculateHubImpact(String hubId, Set<String> servicedAreas) throws SQLException{
        float impact = 0;
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
                impact += postalPopulation * (1/postalHubs);
                postalPop.close();
                statement.close();
                conn.close();
            }
            catch(SQLException e){
                throw e;
            }
        }
        return impact;
    }*/

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


    float postalPopulationOutOfService(String postalCode) throws SQLException{
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