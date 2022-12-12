//this class stores the x,y coordinates of a distribution hub
//in other words, each distribution hub HubImpact object will also contain an object of this class
public class Point {

    private int x;
    private int y;

    Point(int x, int y){
        this.x= x;
        this.y = y;
    }

    int getX(){
        return x;
    }

    int getY(){
        return y;
    }


    //method to compare two Point objects. This method is used during addDistributionHub
    //to check whether a hub already exists at the x,y coordinates that the new hub is passing as its location
    boolean compare(Point location){   //the location parameter is the new hub's location
        if(x==location.getX() && y==location.getY()){  //check the new hub's location against this hub's location
            return true;
        }
        return false;
    }
}
