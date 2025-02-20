package com.gjjfintech.jiradatatransform.model;

import java.util.List;

public class CreateConfluencePageRequestBody {
    private String type;
    private String title;
    private List<Ancestor> ancestors;
    private Space space;
    private Body body;

    // Constructors, getters and setters

    public CreateConfluencePageRequestBody() {
    }

    public CreateConfluencePageRequestBody(String type, String title, List<Ancestor> ancestors, Space space, Body body) {
        this.type = type;
        this.title = title;
        this.ancestors = ancestors;
        this.space = space;
        this.body = body;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Ancestor> getAncestors() {
        return ancestors;
    }

    public void setAncestors(List<Ancestor> ancestors) {
        this.ancestors = ancestors;
    }

    public Space getSpace() {
        return space;
    }

    public void setSpace(Space space) {
        this.space = space;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    // Nested DTO classes

    public static class Ancestor {
        private String id;

        public Ancestor() {
        }

        public Ancestor(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class Space {
        private String key;

        public Space() {
        }

        public Space(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class Body {
        private Storage storage;

        public Body() {
        }

        public Body(Storage storage) {
            this.storage = storage;
        }

        public Storage getStorage() {
            return storage;
        }

        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        public static class Storage {
            private String value;
            private String representation;

            public Storage() {
            }

            public Storage(String value, String representation) {
                this.value = value;
                this.representation = representation;
            }

            public String getValue() {
                return value;
            }

            public void setValue(String value) {
                this.value = value;
            }

            public String getRepresentation() {
                return representation;
            }

            public void setRepresentation(String representation) {
                this.representation = representation;
            }
        }
    }
}
