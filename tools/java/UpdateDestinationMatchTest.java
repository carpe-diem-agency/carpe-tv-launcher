package agency.carpediem.tv.prototype;

/** Tests the production predicate; does not claim Android provider redirect behavior. */
public final class UpdateDestinationMatchTest {
    private static final String MARKER="carpe-update-uuid";

    public static void main(String[] args) {
        String uri="https://example.invalid/a.apk#carpe-update-uuid";
        String path="file:///owned/carpe-update-uuid.apk";
        check(uri,path,uri,null,UpdateDestinationMatch.Result.MATCH,"queued marker without path");
        check(uri,path,uri,path,UpdateDestinationMatch.Result.MATCH,"marker and path");
        check(uri,path,"https://example.invalid/redirected.apk",path,UpdateDestinationMatch.Result.MATCH,"301 changed URI exact path");
        check(uri,path,null,path,UpdateDestinationMatch.Result.MATCH,"missing transport exact path");
        check(uri,path,uri,"file:///owned/other.apk",UpdateDestinationMatch.Result.CONFLICT,"marker wrong destination");
        check(uri,path,"https://other.invalid/a.apk","file:///owned/other.apk",UpdateDestinationMatch.Result.IGNORE,"unrelated row");
        check(uri,path,"https://example.invalid/redirected.apk",null,UpdateDestinationMatch.Result.IGNORE,"uncorrelatable redirected queued row");
        check(uri,path,null,null,UpdateDestinationMatch.Result.IGNORE,"missing both");
        check(uri,path,"different",path+".other",UpdateDestinationMatch.Result.IGNORE,"path prefix cannot match");
        String description="Localized description"+UpdateDestinationMatch.descriptionSuffix(MARKER);
        String redirected="https://example.invalid/redirected.apk";
        check(uri,path,redirected,null,description,UpdateDestinationMatch.Result.MATCH,"description survives redirect before file exists");
        check(uri,path,redirected,"file:///owned/other.apk",description,UpdateDestinationMatch.Result.CONFLICT,"description wrong destination");
        check(uri,path,redirected,null,description+" suffix",UpdateDestinationMatch.Result.IGNORE,"description prefix cannot match");
        check(uri,path,redirected,null,UpdateDestinationMatch.descriptionSuffix(MARKER+"-other"),UpdateDestinationMatch.Result.IGNORE,"different reference cannot match");
        System.out.println("PASS: exact destination redirect fallback, queued marker and wrong-destination rejection");
    }
    private static void check(String expectedUri,String expectedPath,String uri,String path,UpdateDestinationMatch.Result expected,String label) {
        check(expectedUri,expectedPath,uri,path,null,expected,label);
    }
    private static void check(String expectedUri,String expectedPath,String uri,String path,String description,UpdateDestinationMatch.Result expected,String label) {
        if(UpdateDestinationMatch.match(expectedUri,expectedPath,MARKER,uri,path,description)!=expected) throw new AssertionError(label);
    }
}
