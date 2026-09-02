/**
 * Internal Airship Layout Rendering Library
 * @hide
 */
// TODO: Drop the package-level @RestrictTo below.
//
// BCV reads markers off package-info and applies them to the package *and every subpackage*, so
// this single annotation filters the module's entire API dump down to nothing -- including the
// custom-view API that customers actually use (AirshipCustomViewManager, AirshipCustomViewHandler,
// AirshipCustomViewArguments). A publicClasses whitelist can't rescue them: BCV drops non-public
// packages after the whitelist is applied. So until this is removed, api/urbanairship-layout.api
// stays empty and no layout API break gets caught.
//
// Removing it is not a one-liner: with the annotation gone, 233 classes are exposed, mostly in
// .property (114), .event (22), .reporting (13) and .analytics (11). Audit the package first so
// everything that isn't real public API carries its own @RestrictTo and @hide, then delete this
// annotation and re-dump.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
package com.urbanairship.android.layout;

import androidx.annotation.RestrictTo;
