public class DamagedPostalCodes {

    private String postalCodeId;
    private int population;
    private int area;
    private float repairEstimate;

    DamagedPostalCodes(String postalCodeId, int population, int area, float repairEstimate){
        this.postalCodeId = postalCodeId;
        this.population = population;
        this.area = area;
        this.repairEstimate = repairEstimate;
    }

    String getPostalCodeId(){
        return postalCodeId;
    }

    int getPopulation(){
        return population;
    }

    int getArea(){
        return area;
    }

    float getRepairEstimate(){
        return repairEstimate;
    }

    void setPopulation(int population){
        this.population = population;
    }

    void setArea(int area){
        this.area = area;
    }

    void setRepairEstimate(float repairEstimate){
        this.repairEstimate = repairEstimate;
    }
}