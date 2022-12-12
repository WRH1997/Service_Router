import java.util.*;

//this class stores distribution hubs
//that is to say, each distribution hub will be stored in an object of this class
public class HubImpact {

    private String hubId;
    private Point location;
    private float repairTime;
    private boolean inService;
    private Set<String> servicedAreas;
    private float impact;
    private float populationEffected;

    HubImpact(String hubId, Point location, Set<String> servicedAreas, float repairTime, boolean inService, float impact, float populationEffected){
        this.hubId = hubId;
        this.location = location;
        this.servicedAreas = servicedAreas;
        this.repairTime = repairTime;
        this.inService = inService;
        this.impact = impact;
        this.populationEffected = populationEffected;
    }

    String getHubId(){
        return hubId;
    }

    Point getLocation(){
        return location;
    }

    float getRepairTime(){
        return repairTime;
    }

    boolean getInService(){
        return inService;
    }

    Set<String> getServicedAreas(){
        return servicedAreas;
    }

    float getImpact(){
        return impact;
    }

    float getPopulationEffected(){
        return populationEffected;
    }

    void setRepairTime(float repairTime){
        this.repairTime = repairTime;
    }

    void setServicedAreas(Set<String> servicedAreas){
        this.servicedAreas = servicedAreas;
    }

    void setInService(boolean inService){
        this.inService = inService;
    }

    void setImpact(float impact){
        this.impact = impact;
    }

    void setPopulationEffected(float populationEffected){
        this.populationEffected = populationEffected;
    }
}
