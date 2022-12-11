import java.util.*;

public class RepairPlanGrid {

    private String[][] repairPlanGrid;
    Map<String, int[]> hubRepairGridCoordinates;

    RepairPlanGrid(HubImpact startHub, HubImpact endHub, List<HubImpact> intermediateHubs){
        repairPlanGrid = setHubsToGrid(startHub, endHub, intermediateHubs);
    }

    private String[][] setHubsToGrid(HubImpact startHub, HubImpact endHub, List<HubImpact> intermediateHubs){
        hubRepairGridCoordinates = new HashMap<>();
        int startX = startHub.getLocation().getX();
        int startY = startHub.getLocation().getY();
        int endX = endHub.getLocation().getX();
        int endY = endHub.getLocation().getY();
        int maxXCoordinate = Math.abs(endX-startX);
        int maxYCoordinate = Math.abs(endY-startY);
        String[][] repairPlanGrid = new String[maxXCoordinate+1][maxYCoordinate+1];
        /*for(int r=0; r<repairPlanGrid.length; r++){
            for(int c=0; c<repairPlanGrid[0].length; c++){
                repairPlanGrid[r][c] = "";
            }
        }*/
        repairPlanGrid[0][0] = startHub.getHubId();
        hubRepairGridCoordinates.put(startHub.getHubId(), new int[] {0,0});
        repairPlanGrid[maxXCoordinate][maxYCoordinate] = endHub.getHubId();
        hubRepairGridCoordinates.put(endHub.getHubId(), new int[] {maxXCoordinate,maxYCoordinate});
        for(int i=0; i<intermediateHubs.size(); i++){
            int intermediateX = intermediateHubs.get(i).getLocation().getX();
            int intermediateY = intermediateHubs.get(i).getLocation().getY();
            repairPlanGrid[Math.abs(intermediateX-startX)][Math.abs(intermediateY-startY)] = intermediateHubs.get(i).getHubId();
            hubRepairGridCoordinates.put(intermediateHubs.get(i).getHubId(), new int[]{Math.abs(intermediateX-startX), Math.abs(intermediateY-startY)});
        }
        return repairPlanGrid;
    }


    List<HubImpact> findBestRepairPath(List<List<HubImpact>> allPathCombinations, HubImpact endHub){
        Map<List<HubImpact>, Float> repairPathImpacts = new HashMap<>();
        boolean xIsEven = true;
        boolean yIsEven = true;
        if(repairPlanGrid.length%2!=0){
            xIsEven = false;
        }
        if(repairPlanGrid[0].length%2!=0){
            yIsEven = false;
        }
        double diagonalSlope = (double) hubRepairGridCoordinates.get(endHub.getHubId())[1] / (double) hubRepairGridCoordinates.get(endHub.getHubId())[0];
        List<HubImpact> bestPath = null;
        float bestImpact = -1;
        //boolean previousCoordinateBelowDiagonal = true;
        for(List<HubImpact> potentialPath: allPathCombinations){
            boolean xMonotonic = true;
            boolean yMonotonic = true;
            int previousBelowDiagonal = 0;
            int crossedDiagonal = 0;
            int[] currCoordinates = new int[] {0, 0};  //starting coordinates
            for(HubImpact nextHub: potentialPath){
                int[] nextCoordinates = hubRepairGridCoordinates.get(nextHub.getHubId());
                double nextCoordinatePointSlope = diagonalSlope * (double)nextCoordinates[0];
                if(currCoordinates[0]==0 && currCoordinates[1]==0){
                    if((double)nextCoordinates[1]<nextCoordinatePointSlope){
                        previousBelowDiagonal = -1;
                    }
                    else if((double)nextCoordinates[1]>nextCoordinatePointSlope){
                        previousBelowDiagonal = 1;
                    }
                    currCoordinates = nextCoordinates;
                    continue;
                }
                if(nextCoordinates[0]<currCoordinates[0]){
                    xMonotonic = false;
                }
                if(nextCoordinates[1]<currCoordinates[1]){
                    yMonotonic = false;
                }
                if((double)nextCoordinates[1]<nextCoordinatePointSlope){
                    if(previousBelowDiagonal==1){
                        crossedDiagonal++;
                    }
                    previousBelowDiagonal = -1;
                }
                else if((double)nextCoordinates[1]>nextCoordinatePointSlope){
                    if(previousBelowDiagonal==-1){
                        crossedDiagonal++;
                    }
                    previousBelowDiagonal = 1;
                }
                /*if(xIsEven==yIsEven){
                    if(currCoordinates[0]<currCoordinates[1] && nextCoordinates[0]>nextCoordinates[1]){
                        crossedDiagonal++;
                    }
                    if(currCoordinates[0]>currCoordinates[1] && nextCoordinates[0]<nextCoordinates[1]){
                        crossedDiagonal++;
                    }
                }
                else if(xIsEven && !yIsEven){
                    if(currCoordinates[0]<currCoordinates[1] && nextCoordinates[0]>=nextCoordinates[1]){
                        crossedDiagonal++;
                    }
                    if(currCoordinates[0]>=currCoordinates[1] && nextCoordinates[0]<nextCoordinates[1]){
                        crossedDiagonal++;
                    }
                }
                else if(!xIsEven && yIsEven){
                    if(currCoordinates[0]<=currCoordinates[1] && nextCoordinates[0]>nextCoordinates[1]){
                        crossedDiagonal++;
                    }
                    if(currCoordinates[0]>currCoordinates[1] && nextCoordinates[0]<=nextCoordinates[1]){
                        crossedDiagonal++;
                    }
                }*/
                currCoordinates = nextCoordinates;
            }
            if(crossedDiagonal<=1 && (xMonotonic||yMonotonic)){
                float potentialPathImpact = 0;
                for(HubImpact hub: potentialPath){
                    potentialPathImpact += hub.getImpact();
                }
                if(potentialPathImpact>bestImpact){
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
