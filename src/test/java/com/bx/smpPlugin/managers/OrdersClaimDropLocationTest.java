// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;



import org.bukkit.Location;

import org.bukkit.util.Vector;

import org.junit.jupiter.api.Test;



import static org.junit.jupiter.api.Assertions.assertEquals;



class OrdersClaimDropLocationTest {



    @Test

    void claimDropLocationOffsetsAlongLookDirection() {

        Location eye = new Location(null, 0, 65.62, 0);

        Location dropSpot = OrdersManager.claimDropLocation(eye, new Vector(0, 0, 1));

        assertEquals(0D, dropSpot.getX(), 0.001);

        assertEquals(65.62D, dropSpot.getY(), 0.001);

        assertEquals(1.5D, dropSpot.getZ(), 0.001);

    }

}


