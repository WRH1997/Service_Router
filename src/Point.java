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

    boolean compare(Point location){
        if(x==location.getX() && y==location.getY()){
            return true;
        }
        return false;
    }
}
