import java.util.*;

public class HubPathCombinations {

    HubPathCombinations(){

    }

    //https://rosettacode.org/wiki/Power_set#Java
    List<List<HubImpact>> calculateHubSubsets(List<HubImpact> intermediateHubs){
        List<List<HubImpact>> hubSubsets = new ArrayList<>();
        List<HubImpact> emptySubSet = new ArrayList<>();
        hubSubsets.add(emptySubSet);
        for(int i=0; i< intermediateHubs.size(); i++){
            List<List<HubImpact>> subsets = new ArrayList<>();
            for(int k=0; k<hubSubsets.size(); k++){
                subsets.add(hubSubsets.get(k));
                List<HubImpact> nextSubset = new ArrayList<>();
                nextSubset.addAll(hubSubsets.get(k));
                nextSubset.add(intermediateHubs.get(i));
                subsets.add(nextSubset);
            }
            hubSubsets = subsets;
        }
        return hubSubsets;
    }



    //https://www.codingninjas.com/codestudio/library/print-all-possible-permutations-of-a-given-array-without-duplicates
    List<List<HubImpact>> getPathCombinations(List<HubImpact> intermediateHubs){
        List<List<HubImpact>> pathCombinations = new ArrayList<>();
        int size = intermediateHubs.size()-1;
        calculatePaths(pathCombinations, intermediateHubs, 0, size);
        return pathCombinations;
    }



    //https://www.codingninjas.com/codestudio/library/print-all-possible-permutations-of-a-given-array-without-duplicates
    private void calculatePaths(List<List<HubImpact>> pathCombinations, List<HubImpact> intermediateHubs, int counter, int size){
        if(counter==size){
            List<HubImpact> intermediateHubsCopy = deepCopyList(intermediateHubs);
            pathCombinations.add(intermediateHubsCopy);
            return;
        }
        for(int i=counter;i<=size;i++){
            HubImpact before = intermediateHubs.get(counter);
            intermediateHubs.set(counter, intermediateHubs.get(i));
            intermediateHubs.set(i, before);
            calculatePaths(pathCombinations, intermediateHubs, counter+1, size);
            before = intermediateHubs.get(counter);
            intermediateHubs.set(counter, intermediateHubs.get(i));
            intermediateHubs.set(i, before);
        }
    }


    private List<HubImpact> deepCopyList(List<HubImpact> path){
        List<HubImpact> listCopy = new ArrayList<>();
        for(int i=0; i<path.size(); i++){
            listCopy.add(path.get(i));
        }
        return listCopy;
    }
}
