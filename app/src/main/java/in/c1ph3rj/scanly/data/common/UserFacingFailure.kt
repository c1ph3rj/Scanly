package `in`.c1ph3rj.scanly.data.common

/**
 * A failure whose [message] is already written for the user (localized), such as "A tag called
 * “Travel” already exists." Repositories pass it through unchanged; any other failure is shown
 * with their generic message.
 */
internal class UserFacingFailure(message: String) : Exception(message)
