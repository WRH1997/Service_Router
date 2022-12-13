import java.util.ArrayList;
import java.util.List;

//this class provides PowerService with power-set calculations during repairPlan. In other words,
//is used to calculate all the possible subsets of a given set. In particular, it is used to get all
//possible subsets of repairPlan's set of intermediate hubs
public class PowerSets {

    PowerSets(){}


    //this method is used during PowerService's repairPlan to calculate the power-set (all possible subsets)
    //of the set of intermediate hubs. For example, if the intermediate hubs set [A1, B2] is passed to this method,
    //then it will return [[A1], [A1, B2], [B2]]
    //CITATION NOTE: The following URL was referenced and adapted to implement this method's code:
    //URL: https://rosettacode.org/wiki/Power_set#Java
    //Accessed: December 9, 2022
    List<List<HubImpact>> calculateHubSubsets(List<HubImpact> intermediateHubs){
        List<List<HubImpact>> hubSubsets = new ArrayList<>();
        List<HubImpact> emptySubSet = new ArrayList<>();
        hubSubsets.add(emptySubSet);
        for(int i=0; i< intermediateHubs.size(); i++){
            //create temp list of all subsets
            List<List<HubImpact>> subsets = new ArrayList<>();
            //iterate through existing subsets
            for(int k=0; k<hubSubsets.size(); k++){
                //get existing subset and add it to list of temp list of all subsets
                subsets.add(hubSubsets.get(k));
                //create new subset that appends this hub to this existing subset
                List<HubImpact> nextSubset = new ArrayList<>();
                nextSubset.addAll(hubSubsets.get(k));
                nextSubset.add(intermediateHubs.get(i));
                //add this new subset to the temp list of all subsets
                subsets.add(nextSubset);
            }
            //set returned list of all subsets to this temp list of all subsets (this avoids duplicates since each iteration will just expand upon the previous iterations)
            hubSubsets = subsets;
        }
        return hubSubsets;
    }
}
