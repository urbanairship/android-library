# Airship Android SDK 14.x to 15.0 Migration Guide

## Named User replaced with Contact

`NamedUser` has been deprecated in favor of `Contact`. A Contact is distinct from a channel and
represents a "user" within Airship. Contacts may be named and have channels associated with them.

### Enable Contact Features

In order to use the new `Contact`, the corresponding feature must be enabled via `PrivacyManager`:

```java
    UAirship.shared().getPrivacyManager().enable(PrivacyManager.FEATURE_CONTACTS);
```

### Getting and settings IDs

```java
    // 14.x
    NamedUser namedUser = UAirship.shared().getNamedUser();

    namedUser.setId("my-external-id")

    String id = namedUser.getId();

    // 15.x
    Contact contact = UAirship.shared().getContact();

    contact.identify("my-external-id");

    String id = contact.getNamedUserId();
```

### Editing tag groups and attributes

```java
    // 14.x
    TagGroupsEditor tagsEditor = namedUser.editTagGroups();
    AttributeEditor attributeEditor = namedUser.editAttributes();

    // 15.x
    TagGroupsEditor tagsEditor = contact.editTagGroups();
    AttributeEditor attributeEditor = contact.editAttributes();
```

### Clearing IDs

```java
    // 14.x
    namedUser.setId(null);

    // 15.x
    contact.reset();
```
