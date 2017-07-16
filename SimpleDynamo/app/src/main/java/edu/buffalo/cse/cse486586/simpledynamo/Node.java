package edu.buffalo.cse.cse486586.simpledynamo;

import java.util.Comparator;

/**
 * Created by sourav on 4/28/17.
 */

public class Node {

    String Port_id;
    String Node_id;

    public Node(String Port_id, String Node_id){
        this.Port_id = Port_id;
        this.Node_id = Node_id;
    }

}

class Comp implements Comparator<Node>{

    public int compare(Node obj1, Node obj2){

        return obj1.Node_id.compareTo(obj2.Node_id);

    }

}
