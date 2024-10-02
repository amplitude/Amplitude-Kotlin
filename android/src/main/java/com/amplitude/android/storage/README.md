As the Android SDK evolves, we'll end up with different storage configuration. Its important that
we have a single place to keep track of all the storage configurations. 

For every new storage version, please make sure you have a corresponding storage context class which
houses all the storage related fields. Also, please make sure the storage context also outlines
how the data is structured in disk so that when we move to a new version of storage, we can make
sure we don't leave any data behind.