/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dscheck;

/**
 *
 * @author rmfaller
 */
class Tracker {

    boolean validobject;
    String dsetag;
    String name;
    int passed = 0;
    int failed = 0;
    int called = 0;
    long totaltime = 0;
    
    public Tracker() {
    }
    
    public Tracker (String name) {
        this.name = name;
    }
    
    public void putName (String name) {
        this.name = name;
    }
    
    public String getName () {
        return(name);
    }
    
}
