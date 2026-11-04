package com.creationreadingassistant.ui.screen.reader
import org.junit.Test
import org.junit.Assert.*
class ReaderSessionUserInputGuardTest {
 private fun guard()=ReaderSessionPositionGuard().apply { arm(8019,99.17f);anchor(4,1828) }
 @Test fun zeroDragAfterGeometryDoesNotInheritOldAnchor(){val g=guard();g.observe(4,1088);g.beginUserScroll(4,1088);g.endUserScroll(4,1088);assertFalse(g.hasUserMoved());assertEquals(8019,g.resolveNoMovePosition(4,1088)?.first)}
 @Test fun onePixelDrag(){val g=guard();g.beginUserScroll(4,1828);g.observe(4,1829);assertTrue(g.hasUserMoved());assertNull(g.resolveNoMovePosition(4,1829))}
 @Test fun fastExitBeforeObserver(){val g=guard();g.beginUserScroll(4,1828);assertNull(g.resolveNoMovePosition(5,0));assertTrue(g.hasUserMoved())}
 @Test fun releaseBeforeObserver(){val g=guard();g.beginUserScroll(4,1828);g.endUserScroll(5,0);assertTrue(g.hasUserMoved())}
 @Test fun flingRetainsMovementAfterRelease(){val g=guard();g.beginUserScroll(4,1828);g.endUserScroll(4,1829);g.observe(8,0);assertNull(g.resolveNoMovePosition(8,0))}
 @Test fun dragWithoutConsumptionIsNotMovement(){val g=guard();g.beginUserScroll(4,1828);g.endUserScroll(4,1828);g.observe(4,1088);assertFalse(g.hasUserMoved());assertEquals(8019,g.resolveNoMovePosition(4,1088)?.first)}
 @Test fun preAnchorDragDoesNotAuthorizeRestore(){val g=ReaderSessionPositionGuard();g.arm(8019,99.17f);g.beginUserScroll(4,1828);g.observe(5,0);g.anchor(4,1828);g.observe(4,1088);assertFalse(g.hasUserMoved())}
 @Test fun explicitNavigationPersists(){val g=guard();g.recordNavigation();assertTrue(g.hasUserMoved());assertNull(g.resolveNoMovePosition(9,0))}
 @Test fun navigationBeforeRestoreDoesNotOpenGate(){val g=ReaderSessionPositionGuard();g.arm(8019,99.17f);g.recordNavigation();g.anchor(4,1828);assertFalse(g.hasUserMoved())}
 @Test fun disarmClearsInput(){val g=guard();g.beginUserScroll(4,1828);g.disarm();g.anchor(4,1828);g.observe(5,0);assertFalse(g.hasUserMoved());assertNull(g.resolveNoMovePosition(5,0))}
 @Test fun newArmClearsMovement(){val g=guard();g.recordNavigation();g.arm(9000,99.9f);g.anchor(4,1088);g.observe(4,700);assertFalse(g.hasUserMoved());assertEquals(9000,g.resolveNoMovePosition(4,700)?.first)}
}
