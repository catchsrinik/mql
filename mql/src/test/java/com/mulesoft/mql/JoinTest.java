package com.mulesoft.mql;

import static com.mulesoft.mql.ObjectBuilder.newObject;
import static com.mulesoft.mql.Restriction.*;
import static com.mulesoft.mql.JoinBuilder.*;
import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import junit.framework.Assert;

import org.junit.Test;

public class JoinTest extends Assert {
    
    private int executionCount;
    
    @Test
    public void findTotalTweets() throws Exception {
        Query query = new QueryBuilder()
            .from("persons")
            .as("p")
            .join("twitter.getUserInfo(p.twitterId)", "twitterInfo")
            .select(newObject()
                        .set("name", "p.firstName + \" \" + p.lastName")
                        .set("tweets", "twitterInfo.totalTweets"))
            .build();

        Map<String,Object> context = getMockContext();
        Collection<Map> result = query.execute(context);
        
        assertEquals(2, result.size());
        for (Map newPerson : result) {
            if (newPerson.get("name").equals("Joe Schmoe")) {
                assertEquals(2, newPerson.size());
                assertEquals("Joe Schmoe", newPerson.get("name"));
                assertEquals(4, newPerson.get("tweets"));
            } else if (newPerson.get("name").equals("Jane Schmoe")) {
                assertEquals(2, newPerson.size());
                assertEquals("Jane Schmoe", newPerson.get("name"));
                assertEquals(5, newPerson.get("tweets"));
            } else {
                fail("Did not find correct results");
            }
        }
    }

    @Test
    public void findTotalTweetsWithWhere() throws Exception {
        Query query = new QueryBuilder()
            .from("persons")
            .as("p")
            .join("twitter.getUserInfo(p.twitterId)", "twitterInfo")
            .where(lt(property("twitterInfo.totalTweets"), 5))
            .select(newObject()
                        .set("name", "p.firstName + \" \" + p.lastName")
                        .set("tweets", "twitterInfo.totalTweets"))
            .build();
        
        Map<String,Object> context = getMockContext();
        Collection<Map> result = query.execute(context);
        
        assertEquals(1, result.size());

        Iterator<Map> itr = result.iterator();
        Map newPerson = itr.next();
        assertEquals(2, newPerson.size());
        assertEquals("Joe Schmoe", newPerson.get("name"));
        assertEquals(4, newPerson.get("tweets"));
    }


    @Test
    public void testQueryText() throws Exception {
        Map<String, Object> context = getMockContext();
        
        Collection<Map> result = Query.execute(
            "from persons as p join twitter.getUserInfo(p.twitterId) as twitterInfo where twitterInfo.totalTweets < 5" +
            "select new { name = p.firstName + ' ' + p.lastName, tweets = twitterInfo.totalTweets }", 
            context);
        
        assertEquals(1, result.size());

        Iterator<Map> itr = result.iterator();
        Map newPerson = itr.next();
        assertEquals(2, newPerson.size());
        assertEquals("Joe Schmoe", newPerson.get("name"));
        assertEquals(4, newPerson.get("tweets"));
    }

    @Test
    public void joinOnNonNullVariable() throws Exception {
        List<User> persons = new ArrayList<User>();
        
        persons.add(new User("Joe", "Schmoe", "Sales", 10000, "joeschmoe"));
        persons.add(new User("Jane", "Schmoe", "Sales", 12000, null));

        Twitter twitter = getMockTwitter();
        
        Map<String,Object> context = new HashMap<String,Object>();
        context.put("persons", persons);
        context.put("twitter", twitter);
        
        Collection<Map> result = Query.execute(
            "from persons as p join twitter.getUserInfo(p.twitterId) as twitterInfo on p.twitterId " +
            "select new { name = p.firstName + ' ' + p.lastName, tweets = twitterInfo.?totalTweets }", 
            context);
        
        assertEquals(1, result.size());
    }

    @Test
    public void asyncQueryLang() throws Exception {
        Map<String, Object> context = getMockContext();
        
        Collection<Map> result = Query.execute(
            "from persons as p " +
            "join twitter.getUserInfo(p.twitterId) as twitterInfo," +
            "twitter.getUserInfo(p.twitterId) as twitterInfo2 async " +
            "where twitterInfo.totalTweets < 5" +
            "select new { name = p.firstName + ' ' + p.lastName, tweets = twitterInfo.totalTweets }", 
            context);
        
        assertEquals(1, result.size());
        
        result = Query.execute(
           "from persons as p " +
           "join twitter.getUserInfo(p.twitterId) as twitterInfo async(5) " +
           "where twitterInfo.totalTweets < 5" +
           "select new { name = p.firstName + ' ' + p.lastName, tweets = twitterInfo.totalTweets }", 
           context);
       
       assertEquals(1, result.size());
    }

    protected Map<String, Object> getMockContext() {
        List<User> persons = getPersons();
        Twitter twitter = getMockTwitter();
        
        Map<String,Object> context = new HashMap<String,Object>();
        context.put("persons", persons);
        context.put("twitter", twitter);
        return context;
    }

    protected Twitter getMockTwitter() {
        Twitter twitter = createMock(Twitter.class);
        
        UserInfo joe = createMock(UserInfo.class);
        expect(twitter.getUserInfo("joeschmoe")).andStubReturn(joe);
        expect(joe.getTotalTweets()).andStubReturn(4);
        
        UserInfo jane = createMock(UserInfo.class);
        expect(twitter.getUserInfo("janeschmoe")).andStubReturn(jane);
        expect(jane.getTotalTweets()).andStubReturn(5);
        
        replay(twitter, joe, jane);
        return twitter;
    }
    
    private List<User> getPersons() {
        List<User> persons = new ArrayList<User>();
        
        persons.add(new User("Joe", "Schmoe", "Sales", 10000, "joeschmoe"));
        persons.add(new User("Jane", "Schmoe", "Sales", 12000, "janeschmoe"));
        
        return persons;
    }

    @Test
    public void asyncJoin() throws Exception {
        Executor executor = new Executor() {
            
            public void execute(Runnable command) {
                command.run();
                executionCount++;
            }
        };
        
        Query query = new QueryBuilder()
            .from("persons")
            .as("p")
            .join(expression("twitter.getUserInfo(p.twitterId)", "twitterInfo")
                  .async().executor(executor))
            .select(newObject()
                        .set("name", "p.firstName + \" \" + p.lastName")
                        .set("tweets", "twitterInfo.totalTweets"))
            .build();

        Map<String,Object> context = getMockContext();
        Collection<Map> result = query.execute(context);
        
        assertEquals(2, executionCount);
        assertEquals(2, result.size());

        Iterator<Map> itr = result.iterator();
        Map newPerson = itr.next();
        assertEquals(2, newPerson.size());
        assertEquals("Joe Schmoe", newPerson.get("name"));
        assertEquals(4, newPerson.get("tweets"));

        newPerson = itr.next();
        assertEquals(2, newPerson.size());
        assertEquals("Jane Schmoe", newPerson.get("name"));
        assertEquals(5, newPerson.get("tweets"));
    }
    
    public interface Twitter {
        UserInfo getUserInfo(String twitterId);
    }

    public interface UserInfo {
        int getTotalTweets();
    }
}
