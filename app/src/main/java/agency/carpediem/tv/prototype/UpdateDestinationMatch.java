package agency.carpediem.tv.prototype;

/** Exact correlation of app-owned provider rows with an already validated enqueue journal. */
final class UpdateDestinationMatch {
    enum Result { MATCH, IGNORE, CONFLICT }
    static String descriptionSuffix(String requestMarker) {return "\n["+requestMarker+"]";}

    static Result match(String expectedTransport,String expectedDestination,String expectedMarker,
                        String transport,String destination,String description) {
        // A permanent redirect can replace the request URI before the provider knows the local URI.
        boolean marker=expectedTransport.equals(transport)
                ||(description!=null&&description.endsWith(descriptionSuffix(expectedMarker)));
        boolean hasDestination=destination!=null&&!destination.isEmpty();
        if(marker) return hasDestination&&!expectedDestination.equals(destination)?Result.CONFLICT:Result.MATCH;
        // HTTP redirects can replace the provider URI. The unique journal path remains authoritative.
        return hasDestination&&expectedDestination.equals(destination)?Result.MATCH:Result.IGNORE;
    }
    private UpdateDestinationMatch() {}
}
