With the use of robolectric, testing now uses junit4 and runs in the jvm
instaed of the dvm. This allows us to write more advanced tests, be able
to use a mocking framework, test more of our library, and makes test run
much quicker. At this moment, Roblectric 2.0 alpha 2 is being used.
Roblectric 2.0 is to remove more of the shadow code to produce a much
more accurate simulation.  http://robolectric.blogspot.com/

-- Robolectric --
The project was set up following 
http://pivotal.github.io/robolectric/eclipse-quick-start.html.  That
mostly worked, except our tests also require the robolectric jar to
be above the android-sdk jar in "Build Path" ->
"Configure Build Path..." -> "Order and Export".

If an error occurs that robolectric can not find your android-sdk, then
follow one of the ways to resolve the issue
here: http://pivotal.github.io/robolectric/resources.html.
Easiest way to fix this is to navigate to the android-lib/UrbanAirshipLib
and run `android update-project -p .`. This will create a local.properties
with the path to the android sdk.

UATestRunner.java:
Some of the unit tests require a test asset directory.  Since by default the
robolectric test run in the UrbanAirshipLib directory, a custom test runner
was created to set up to use the test assets.

ShadowObjects:
Shadow objects just give behavior to stripped down classes in the sdk jar.
For the most part, most of the shadow objects exist for the library with a few
exceptions.  A custom shadow class was added for content provider to provide
functionality for the getType method.  The use of DatabaseUtils.InsertHelper
was removed in our library because this class is now deprecated, and creating
a shadow class for the behavior was much more difficult then replacing it with
SQLiteStatement.

Documentation:
Since this is alpha, the easiest way to look up documentation is to just look
at the repo directly https://github.com/pivotal/robolectric.  The javadocs will
hopefully be updated once 2.0 is actually out.

Found issues:
LocationManager does not have a complete shadow class, so the testing/mocking
android api for setting locations is not available. To get around this, either
extend the shadow object for location manager or use the shadow implementation
to set the last known location of a provider. Example:
`
lm = (LocationManager) app.getApplicationContext()
				.getSystemService(Context.LOCATION_SERVICE);

ShadowLocationManager shadowLocationManager = Robolectric.shadowOf(lm);
shadowLocationManager.setLastKnownLocation(provider, location);
`

-- Running --
Ant:
Currently it depends on the deploy script to be run first.

Copy build.properties.sample to build.properties.  Update the location of the
android-sdk, then to run it:  `ant`.  To clean it: `ant clean`.

Any file in the test directory will be run as tests if it ends with "Test".
"BaseTest" is excluded though.

Android Studio:
Should just be able to select the project and click run.


-- Mocking --
Unit testing should test a single class's methods.  When other classes are
involved, this creates dependencies and testing can then become very difficult.
Mocking is used to to control a dependencies input and output to make testing
easier.  Depending on the class, this is not always needed.  Currently we are
using Mockito as the mocking framework https://code.google.com/p/mockito/.  It
has very good documentation and an easy to use api
http://docs.mockito.googlecode.com/hg/latest/org/mockito/Mockito.html.

Example Mocking Location with Mockito:
`
Location location = Mockito.mock(Location.class);
when(location.getProvider()).thenReturn("GPS");
`

-- Writing Tests --
When adding a new test file, create the test class in the test src directory
in the same package as the class you are testing.  The test file should be 
named exactly the same, except with a "Test" suffix.

Example: 
Class to test: 	src/com/urbanairship/location/LocationService.java
Test file:		test/com/urbanairship/location/LocationServiceTest.java

If the tests depend on having UrbanAirShip started, the new tests should 
probably extend UrbanAirShipBaseTest.  If not, it can probably just use
"@RunWith(UATestRunner.class)" annotation above the class declaration which 
just sets the correct manifest entries if the android app needs to be created.

Other than using Robolectric and Mockito, its just junit testing.  A good
tutorial on junit: http://www.vogella.com/articles/JUnit/article.html
