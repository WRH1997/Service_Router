import java.util.*;


//this class is used by PowerService's repairPlan method to calculate all possible path combinations between startHub, intermediate hubs, and endHub.
//For example, if startHub is "A1" and endHub is "D4" with possible intermediate hubs "B2" and "C3", then this class is used to return
//all possible path combinations (excluding the startHub) which are: [D4], [B2, D4], [C3, D4], [B2, C3, D4], and [C3, B2, D4].
public class HubPathCombinations {

    HubPathCombinations(){
    }

    //CITATION NOTE: The following URL was referenced and adapted to implement this method's code:
    //URL: https://www.codingninjas.com/codestudio/library/print-all-possible-permutations-of-a-given-array-without-duplicates
    //Accessed: December 9, 2022.
    //method to find all possible path combinations (permutations) from a set of intermediate hubs
    List<List<HubImpact>> getPathCombinations(List<HubImpact> intermediateHubs){
        List<List<HubImpact>> pathCombinations = new ArrayList<>();
        //define recursive base case
        int size = intermediateHubs.size()-1;
        //call recursive permutation function onto set of intermediate hubs set
        calculatePaths(pathCombinations, intermediateHubs, 0, size);
        return pathCombinations;
    }




    //CITATION NOTE: The following URL was referenced and adapted to implement this method's code:
    //URL: https://www.codingninjas.com/codestudio/library/print-all-possible-permutations-of-a-given-array-without-duplicates
    //Accessed: December 9, 2022.
    //recursive method that find all possible permutations of intermediate hub (i.e., finds all possible paths between them)
    private void calculatePaths(List<List<HubImpact>> pathCombinations, List<HubImpact> intermediateHubs, int counter, int size){
        if(counter==size){   //check for recursive base case
            //add the swapped set of intermediate hub (permutation) to list of all possible paths
            List<HubImpact> intermediateHubsCopy = deepCopyList(intermediateHubs);
            pathCombinations.add(intermediateHubsCopy);
            //stop the recursive loop
            return;
        }
        for(int i=counter;i<=size;i++){
            //swap hub at index <counter> with hub at index <i>
            HubImpact before = intermediateHubs.get(counter);
            intermediateHubs.set(counter, intermediateHubs.get(i));
            intermediateHubs.set(i, before);
            //recursively call this method to start swapping hub at <counter+1>
            calculatePaths(pathCombinations, intermediateHubs, counter+1, size);
            //backtrack swapping (revert intermediate hubs list to original order)
            before = intermediateHubs.get(counter);
            intermediateHubs.set(counter, intermediateHubs.get(i));
            intermediateHubs.set(i, before);
        }
    }



    //method used during calculatePaths that returns a deep copy of a HubImpact list
    private List<HubImpact> deepCopyList(List<HubImpact> path){
        List<HubImpact> listCopy = new ArrayList<>();
        for(int i=0; i<path.size(); i++){
            listCopy.add(path.get(i));
        }
        return listCopy;
    }
}
