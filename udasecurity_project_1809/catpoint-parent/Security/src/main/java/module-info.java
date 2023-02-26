module Security {
    requires java.desktop;
    requires miglayout;
    requires Image;
    requires com.google.common;
    requires java.prefs;
    requires com.google.gson;
    opens com.udacity.security.data to com.google.gson;
}