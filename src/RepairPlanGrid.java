import java.util.*;

//this class is used during PowerService's repairPlan method. It stores the hubs in range onto a 2d array which represents a locational grid,
//and also calculates the best possible valid path between the startHub and endHub that repairs the most impactful set of intermediate hubs
public class RepairPlanGrid {

    private String[][] repairPlanGrid;   //2d array to represent a locational grid
    Map<String, int[]> hubRepairGridCoordinates;   //a map to store each hub in range and their coordinates relative to the grid (i.e., not their Point location values, but rather their coordinates relative to the starting hub on the grid)


    RepairPlanGrid(HubImpact startHub, HubImpact endHub, List<HubImpact> intermediateHubs){
        repairPlanGrid = setHubsToGrid(startHub, endHub, intermediateHubs);   //set up and populate the location grid according to the hubs in range
    }


    //method to set up and populate the locational grid and coordinates map. This method is invoked during the constructor
    private String[][] setHubsToGrid(HubImpact startHub, HubImpact endHub, List<HubImpact> intermediateHubs){
        hubRepairGridCoordinates = new HashMap<>();
        int startX = startHub.getLocation().getX();
        int startY = startHub.getLocation().getY();
        int endX = endHub.getLocation().getX();
        int endY = endHub.getLocation().getY();
        //calculate the endHub's grid coordinates (coordinates relative to startHub, where startHub is placed at [0][0] on the grid)
        //these coordinates also represent the dimensions of the grid (i.e., the height and width of the rectangle that forms between startHub and endHub)
        int maxXCoordinate = Math.abs(endX-startX);
        int maxYCoordinate = Math.abs(endY-startY);
        //create a grid using the dimensions calculated
        String[][] repairPlanGrid = new String[maxXCoordinate+1][maxYCoordinate+1];
        //add startHub and endHub onto the grid and store their grid coordinates in the coordinates map
        repairPlanGrid[0][0] = startHub.getHubId();
        hubRepairGridCoordinates.put(startHub.getHubId(), new int[] {0,0});
        repairPlanGrid[maxXCoordinate][maxYCoordinate] = endHub.getHubId();
        hubRepairGridCoordinates.put(endHub.getHubId(), new int[] {maxXCoordinate,maxYCoordinate});
        for(int i=0; i<intermediateHubs.size(); i++){   //iterate through the hubs between startHub and endHub
            int intermediateX = intermediateHubs.get(i).getLocation().getX();
            int intermediateY = intermediateHubs.get(i).getLocation().getY();
            //set this intermediate hub onto the grid (relative to startHub) and add its coordinates to the map
            repairPlanGrid[Math.abs(intermediateX-startX)][Math.abs(intermediateY-startY)] = intermediateHubs.get(i).getHubId();
            hubRepairGridCoordinates.put(intermediateHubs.get(i).getHubId(), new int[]{Math.abs(intermediateX-startX), Math.abs(intermediateY-startY)});
        }
        return repairPlanGrid;
    }



    //this method identifies the best possible path between startHub, intermediate hubs, and endHub. It is supplied with all possible path combinations as its argument.
    //For each of those path combinations, it checks whether it is valid (i.e., either x or y monotonic and crosses diagonal no more than once).
    //Then returns the valid path combination that has the highest total impact
    //For example, if startHub is "A1" and endHub is "D4" with possible intermediate hubs "B2" and "C3", then all possible path combinations excluding the startHub
    //are [D4], [B2, D4], [C3, D4], [B2, C3, D4], and [C3, B2, D4]. So, these combinations are supplied to this method as arguments. Then, this method checks
    //which of these paths is valid. Then, of those valid paths, it returns the one with the highest overall impact
    List<HubImpact> findBestRepairPath(List<List<HubImpact>> allPathCombinations, HubImpact endHub){
        //calculate the slope diagonal line of the rectangle between startHub and endHub using the formula m = (y2-y1)/(x2-x1)
        double diagonalSlope = (double) hubRepairGridCoordinates.get(endHub.getHubId())[1] / (double) hubRepairGridCoordinates.get(endHub.getHubId())[0];
        List<HubImpact> bestPath = null;   //variable to store the best path (i.e., path with highest impact)
        float bestImpact = -1;  //variable to store the best path's impact value
        for(List<HubImpact> potentialPath: allPathCombinations){   //iterate through the list of all possible path combinations
            boolean xMonotonic = true;
            boolean yMonotonic = true;
            int previousBelowDiagonal = 0;   //variable to track whether the last point was below, on, or above the diagonal (-1=below, 0=on, 1=above)
            int crossedDiagonal = 0;   //variable to keep track of how many times this path cross the diagonal
            int[] currCoordinates = new int[] {0, 0};  //starting coordinates (i.e., startHub)
            for(HubImpact nextHub: potentialPath){   //iterate through each hub on this path
                int[] nextCoordinates = hubRepairGridCoordinates.get(nextHub.getHubId());
                double nextCoordinatePointSlope = diagonalSlope * (double)nextCoordinates[0];   //multiply this hub's x coordinate with the slope of the diagonal line (m) to get the y coordinate of the diagonal line at this hub's x coordinate (i.e., what are this hub's x,y if it were on the diagonal line)
                if(currCoordinates[0]==0 && currCoordinates[1]==0){   //this is the first hub after startHub (we don't need to check whether monotonic)
                    if((double)nextCoordinates[1]<nextCoordinatePointSlope){   //this hub is under the diagonal
                        previousBelowDiagonal = -1;
                    }
                    else if((double)nextCoordinates[1]>nextCoordinatePointSlope){   //this hub is below the diagonal
                        previousBelowDiagonal = 1;
                    }
                    currCoordinates = nextCoordinates;
                    continue;
                }
                if(nextCoordinates[0]<currCoordinates[0]){   //path is moving back horizontally (no longer x monotonic)
                    xMonotonic = false;
                }
                if(nextCoordinates[1]<currCoordinates[1]){   //path is moving down vertically (no longer y monotonic)
                    yMonotonic = false;
                }
                if((double)nextCoordinates[1]<nextCoordinatePointSlope){   //this hub is below the diagonal
                    if(previousBelowDiagonal==1){   //the hub before this hub was above the diagonal
                        crossedDiagonal++;   //thus, the path has crossed the diagonal
                    }
                    previousBelowDiagonal = -1;
                }
                else if((double)nextCoordinates[1]>nextCoordinatePointSlope){   //this hub is above the diagonal
                    if(previousBelowDiagonal==-1){   //the hub before this hub was below the diagonal
                        crossedDiagonal++;   //thus, the path has crossed the diagonal
                    }
                    previousBelowDiagonal = 1;
                }
                currCoordinates = nextCoordinates;
            }
            if(crossedDiagonal<=1 && (xMonotonic||yMonotonic)){   //check if the path is valid
                float potentialPathImpact = 0;
                for(HubImpact hub: potentialPath){  //calculate its impact
                    potentialPathImpact += hub.getImpact();
                }
                if(potentialPathImpact>=bestImpact){   //store this path if it is the best path so far
                    bestImpact = potentialPathImpact;
                    bestPath = potentialPath;
                }
            }
        }
        return bestPath;
    }


    String[][] getRepairPlanGrid(){
        return repairPlanGrid;
    }


    Map<String, int[]> getHubRepairGridCoordinates(){
        return hubRepairGridCoordinates;
    }
}
