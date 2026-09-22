package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandSyntaxTest {
    @Test void incompleteChatSettingsReturnSpecificGuidance(){
        var colour=assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookchat",new String[]{"colour"}));
        assertTrue(colour.getMessage().contains("Choose a colour"));
        var pronouns=assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookchat",new String[]{"pronouns"}));
        assertTrue(pronouns.getMessage().contains("Choose pronouns"));
    }
    @Test void helpAndPreviewCannotSilentlyIgnoreExtraArguments(){
        for(String action:new String[]{"help","preview"})assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookchat",new String[]{action,"unexpected"}));
        assertTrue(CommandSyntax.chatHelp(new String[]{}));assertTrue(CommandSyntax.chatHelp(new String[]{"HELP"}));
        assertFalse(CommandSyntax.chatHelp(new String[]{"preview"}));
    }
    @Test void paymentsRequireExactlyAPlayerAndAmount(){
        for(String[] args:new String[][]{{"pay"},{"pay","Alex"},{"pay","Alex","1","extra"}})
            assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nooks",args));
        assertDoesNotThrow(()->CommandSyntax.check("nooks",new String[]{"PAY","Alex","1.25"}));
    }
    @Test void rentalInputsRejectFractionsOverflowAndInvalidRoles(){
        for(String value:new String[]{"half","1.5","0","5","999999999999999999999"})
            assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookplots",new String[]{"prepay","one",value}));
        assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookplots",new String[]{"role","one","Alex","admin"}));
        assertDoesNotThrow(()->CommandSyntax.check("nookplots",new String[]{"invite","one","Alex","BOTH"}));
        assertDoesNotThrow(()->CommandSyntax.check("nookplots",new String[]{"role","one","Alex","build_stock"}));
    }
    @Test void staffMutationsRequireAuditReasonsAndValidSwitches(){
        assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookadmin",new String[]{"give","Alex","10"}));
        assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookadmin",new String[]{"awards","maybe"}));
        assertThrows(IllegalArgumentException.class,()->CommandSyntax.check("nookadmin",new String[]{"plotabsence","one","1.5","holiday"}));
        assertDoesNotThrow(()->CommandSyntax.check("nookadmin",new String[]{"take","Alex","10","incorrect","bonus"}));
        assertDoesNotThrow(()->CommandSyntax.check("nookadmin",new String[]{"plotabsence","one","OFF","returned","early"}));
    }
    @Test void unknownCommandsAreNotTreatedAsHelpOrPreview(){
        for(String root:new String[]{"nooks","nookplots","nookchat","nookadmin"}){
            var error=assertThrows(IllegalArgumentException.class,()->CommandSyntax.check(root,new String[]{"typo"}));
            assertTrue(error.getMessage().contains("/"+root+" help"));
        }
    }
    @Test void shortInvitationsAndConfiguredMultiwordPronounsStayValid(){
        assertDoesNotThrow(()->CommandSyntax.check("nookplots",new String[]{"accept"}));
        assertDoesNotThrow(()->CommandSyntax.check("nookplots",new String[]{"decline","Alex"}));
        assertDoesNotThrow(()->CommandSyntax.check("nookchat",new String[]{"pronouns","ask","me"}));
        assertDoesNotThrow(()->CommandSyntax.check("nookplots",new String[]{"abandon","one","confirm"}));
    }
}
